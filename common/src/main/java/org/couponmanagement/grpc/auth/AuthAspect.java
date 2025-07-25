package org.couponmanagement.grpc.auth;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.couponmanagement.grpc.annotation.RequireAuth;
import org.couponmanagement.grpc.client.AuthContext;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

@Aspect
@Component
public class AuthAspect {
    @Around("@annotation(requireAuth)")
    public Object checkPermissions(ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        List<String> requiredPermissions = List.of(requireAuth.value());
        List<String> callerPermissions = AuthContext.currentListPermissions();

        if (!new HashSet<>(callerPermissions).containsAll(requiredPermissions)) {
            throw new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription(requireAuth.errorMessage()));
        }

        return joinPoint.proceed();
    }
}
