package com.workflow.config;

import com.workflow.assignment.AssignmentNativeWriteInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.handler.MappedInterceptor;

@Configuration(proxyBeanMethods=false)
public class AssignmentNativeWriteConfiguration {
    private final AssignmentNativeWriteInterceptor interceptor;
    public AssignmentNativeWriteConfiguration(AssignmentNativeWriteInterceptor interceptor) { this.interceptor=interceptor; }
    /** Flowable uses WebMvcConfigurationSupport in a child servlet; it does not delegate to WebMvcConfigurer. */
    @Bean public MappedInterceptor assignmentNativeWrites() { return new MappedInterceptor(null,interceptor); }
    @Bean public MappedInterceptor assignmentNativeJobWrites(com.workflow.assignment.AssignmentNativeJobWriteInterceptor jobs) {
        return new MappedInterceptor(null,jobs);
    }
    @Bean public MappedInterceptor assignmentNativeHistoryWrites(com.workflow.assignment.AssignmentNativeHistoryWriteInterceptor history) {
        return new MappedInterceptor(null,history);
    }
}
