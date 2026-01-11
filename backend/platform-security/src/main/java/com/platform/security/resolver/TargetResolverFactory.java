package com.platform.security.resolver;

import com.platform.security.enums.AssignmentTargetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 目标解析器工厂
 * 管理所有目标解析器，根据目标类型返回对应的解析器
 */
@Slf4j
@Component
public class TargetResolverFactory {
    
    private final Map<AssignmentTargetType, TargetResolver> resolvers;
    
    public TargetResolverFactory(List<TargetResolver> resolverList) {
        this.resolvers = new EnumMap<>(AssignmentTargetType.class);
        for (TargetResolver resolver : resolverList) {
            resolvers.put(resolver.getTargetType(), resolver);
            log.info("Registered target resolver for type: {}", resolver.getTargetType());
        }
    }
    
    /**
     * 获取指定类型的解析器
     * @param targetType 目标类型
     * @return 对应的解析器
     * @throws IllegalArgumentException 如果没有找到对应的解析器
     */
    public TargetResolver getResolver(AssignmentTargetType targetType) {
        TargetResolver resolver = resolvers.get(targetType);
        if (resolver == null) {
            throw new IllegalArgumentException("No resolver found for target type: " + targetType);
        }
        return resolver;
    }
    
    /**
     * 检查是否支持指定的目标类型
     */
    public boolean supports(AssignmentTargetType targetType) {
        return resolvers.containsKey(targetType);
    }
}
