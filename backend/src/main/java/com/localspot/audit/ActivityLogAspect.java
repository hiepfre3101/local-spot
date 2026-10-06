package com.localspot.audit;

import com.localspot.entity.ActivityLog;
import com.localspot.entity.User;
import com.localspot.repository.ActivityLogRepository;
import com.localspot.security.AuthenticatedUser;
import com.localspot.service.ClientInfo;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi {@code activity_log} quanh phương thức có {@link AuditedAction} (FR-42) — một chỗ duy nhất thay vì rải lệnh ghi
 * log tay ở từng thao tác, nên thao tác kiểm duyệt mới chỉ cần thêm annotation.
 *
 * <p><b>Cùng transaction với thao tác</b>: aspect có độ ưu tiên thấp nhất, advice transaction được đặt ưu tiên cao hơn
 * ({@code TransactionConfig}) → transaction bọc ngoài, aspect chạy bên trong. Nhờ vậy nhật ký không bao giờ ghi một thao
 * tác đã rollback (vd. 409 optimistic lock khi commit), và không có thao tác nào thành công mà thiếu log. Aspect tự kiểm
 * tra có transaction thật — cấu hình sai thì lỗi ngay thay vì âm thầm ghi log ở transaction riêng.
 *
 * <p>Chỉ ghi khi phương thức trả về bình thường: thao tác bị từ chối (409, 422…) không phải là thao tác đã thực hiện.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ActivityLogAspect {

    private final ActivityLogRepository logs;
    private final EntityManager entityManager;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    public ActivityLogAspect(ActivityLogRepository logs, EntityManager entityManager) {
        this.logs = logs;
        this.entityManager = entityManager;
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, AuditedAction audited) throws Throwable {
        Object result = joinPoint.proceed();

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("@AuditedAction phải chạy trong transaction: " + joinPoint.getSignature());
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNames);
        context.setVariable("result", result);

        Long targetId = evaluate(audited.targetId(), context, Long.class);
        if (targetId == null) {
            throw new IllegalStateException("targetId của @AuditedAction trả null: " + joinPoint.getSignature());
        }
        logs.save(new ActivityLog(
                entityManager.getReference(User.class, currentActorId()),
                audited.action(),
                audited.targetType(),
                targetId,
                metadata(audited, context),
                currentIp()));
        return result;
    }

    private Map<String, Object> metadata(AuditedAction audited, MethodBasedEvaluationContext context) {
        if (audited.metadata().isEmpty()) {
            return null;
        }
        Map<?, ?> raw = evaluate(audited.metadata(), context, Map.class);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        raw.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        return metadata;
    }

    private <T> T evaluate(String expression, MethodBasedEvaluationContext context, Class<T> type) {
        return expressions.computeIfAbsent(expression, parser::parseExpression).getValue(context, type);
    }

    /** Thao tác quản trị luôn có người thực hiện đã đăng nhập — thiếu là lỗi cấu hình, không ghi log "vô chủ". */
    private static Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new IllegalStateException("@AuditedAction cần người dùng đã đăng nhập");
    }

    /** IP người thao tác (sau nginx đã là IP thật — {@code forward-headers-strategy}); ngoài request HTTP → null. */
    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return new ClientInfo(null, attributes.getRequest().getRemoteAddr()).ipAddress();
        }
        return null;
    }
}
