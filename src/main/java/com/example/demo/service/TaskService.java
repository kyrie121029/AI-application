package com.example.demo.service;

import com.example.demo.dto.AIResult;
import com.example.demo.dto.CreateTaskRequest;
import com.example.demo.dto.CreateTaskResponse;
import com.example.demo.dto.MockResultResponse;
import com.example.demo.dto.TaskResponse;
import com.example.demo.enums.TaskStatus;
import com.example.demo.exception.AIResponseParseException;
import com.example.demo.exception.AIServiceException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.model.AIUsageLog;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import com.example.demo.repository.AIUsageLogRepository;
import com.example.demo.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private final TaskRepository taskRepository;
    private final AIService aiService;
    private final AIUsageLogRepository usageLogRepository;

    public TaskService(TaskRepository taskRepository, AIService aiService,
                       AIUsageLogRepository usageLogRepository) {
        this.taskRepository = taskRepository;
        this.aiService = aiService;
        this.usageLogRepository = usageLogRepository;
    }

    @Transactional
    public CreateTaskResponse createTask(CreateTaskRequest request, User user) {
        Task task = new Task(null, request.getTitle(), request.getTaskType(), request.getInputText());
        task.setUser(user);
        task = taskRepository.save(task);
        log.info("任务创建成功: id={}, title={}, userId={}", task.getId(), task.getTitle(), user.getId());
        return new CreateTaskResponse(task.getId(), task.getStatus());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id, User user) {
        return TaskResponse.from(findTaskOrThrow(id, user));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasks(String status, String taskType, String keyword, User user, Pageable pageable) {
        Specification<Task> spec = (root, query, cb) ->
                cb.equal(root.get("user").get("id"), user.getId());
        if (status != null && !status.isBlank())
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), TaskStatus.valueOf(status)));
        if (taskType != null && !taskType.isBlank())
            spec = spec.and((root, query, cb) -> cb.equal(root.get("taskType"), taskType));
        if (keyword != null && !keyword.isBlank())
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(root.get("title"), "%" + keyword + "%"),
                    cb.like(root.get("inputText"), "%" + keyword + "%")));
        return taskRepository.findAll(spec, pageable).map(TaskResponse::from);
    }

    @Transactional
    public void deleteTask(Long id, User user) {
        Task task = findTaskOrThrow(id, user);
        taskRepository.deleteById(task.getId());
        log.info("任务已删除: id={}, userId={}", id, user.getId());
    }

    /**
     * 生成 AI 结果 —— 事务拆分版
     * <p>
     * 旧版：整个方法一个 @Transactional，AI 调用期间占用数据库连接 60+ 秒。
     * 新版：
     *   ① findTaskOrThrow 在事务内（读）
     *   ② AI 调用在事务外（不占数据库连接）
     *   ③ saveAIResultAndLog 在事务内（写）
     *   ④ 失败日志用 REQUIRES_NEW，确保不被主事务回滚
     */
    public MockResultResponse generateResult(Long id, User user) {
        // ① 查任务（在事务内）
        Task task = findTaskOrThrow(id, user);
        long start = System.currentTimeMillis();
        String requestPreview = task.getInputText().substring(0, Math.min(500, task.getInputText().length()));

        // ② 调用 AI —— 在事务外！
        AIResult aiResult;
        try {
            aiResult = aiService.analyze(task.getInputText(), task.getTaskType());
        } catch (AIServiceException e) {
            long elapsed = System.currentTimeMillis() - start;
            saveFailureLogNewTx(user.getId(), task.getId(), elapsed, requestPreview,
                    e.getErrorType());
            throw e;
        }

        long elapsed = System.currentTimeMillis() - start;
        validateAIResult(aiResult);

        // ③ 保存结果 + 日志（短事务）
        return saveAIResultAndLog(task, user, aiResult, elapsed, requestPreview);
    }

    @Transactional
    private MockResultResponse saveAIResultAndLog(Task task, User user, AIResult aiResult,
                                                   long elapsed, String requestPreview) {
        task.setResult(aiResult.toJson());
        task.setStatus(TaskStatus.RESULT_GENERATED);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        AIUsageLog usageLog = new AIUsageLog(
                user.getId(), task.getId(), aiService.getProvider(),
                aiResult.getModelName(), aiResult.getInputTokens(),
                aiResult.getOutputTokens(), elapsed, requestPreview,
                aiService.getPromptVersion()
        );
        usageLogRepository.save(usageLog);

        log.info("AI 结果已生成: id={}, provider={}, promptVersion={}, tokens={}/{}",
                task.getId(), aiService.getProvider(), aiService.getPromptVersion(),
                aiResult.getInputTokens(), aiResult.getOutputTokens());

        MockResultResponse r = new MockResultResponse();
        r.setTaskId(task.getId());
        r.setSummary(aiResult.getSummary());
        r.setConclusion(aiResult.getConclusion());
        r.setSuggestion(aiResult.getSuggestion());
        r.setStatus(task.getStatus());
        r.setModelName(aiResult.getModelName());
        r.setTokensUsed(aiResult.getTokensUsed());
        return r;
    }

    /** 失败日志用独立事务，确保不被主流程回滚 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveFailureLogNewTx(Long userId, Long taskId, long elapsedMs,
                                      String requestPreview, String errorType) {
        try {
            AIUsageLog failLog = new AIUsageLog(userId, taskId, aiService.getProvider(),
                    null, elapsedMs, requestPreview, errorType,
                    aiService.getPromptVersion());
            usageLogRepository.save(failLog);
        } catch (Exception logEx) {
            log.error("保存 AI 失败日志时出错", logEx);
        }
    }

    private void validateAIResult(AIResult result) {
        if (result.getSummary() == null || result.getSummary().isBlank())
            throw new AIResponseParseException("AI 返回摘要为空", "null");
        if (result.getConclusion() == null || result.getConclusion().isBlank())
            throw new AIResponseParseException("AI 返回结论为空", "null");
    }

    private Task findTaskOrThrow(Long id, User user) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> { log.warn("任务不存在: id={}", id); return new TaskNotFoundException(id); });
        if (!task.getUser().getId().equals(user.getId()))
            throw new ForbiddenException("无权访问该任务");
        return task;
    }
}
