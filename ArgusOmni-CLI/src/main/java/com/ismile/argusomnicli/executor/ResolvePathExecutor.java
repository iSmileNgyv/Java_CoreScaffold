package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.extractor.ResponseExtractor;
import com.ismile.argusomnicli.model.ResolvePathConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.VariableResolver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Path resolution executor implementation.
 * Maps logical paths to physical filesystem paths.
 * Follows Open/Closed Principle - extends AbstractExecutor without modifying it.
 * Follows Single Responsibility - only resolves paths.
 */
@Component
public class ResolvePathExecutor extends AbstractExecutor {

    public ResolvePathExecutor(VariableResolver variableResolver,
                              ResponseExtractor responseExtractor) {
        super(variableResolver, responseExtractor);
    }

    @Override
    public boolean supports(TestStep step) {
        return step.getType() == StepType.RESOLVE_PATH && step.getResolvePath() != null;
    }

    @Override
    protected Object doExecute(TestStep step, ExecutionContext context) throws Exception {
        ResolvePathConfig config = step.getResolvePath();

        String repoId = variableResolver.resolve(config.getRepoId(), context.getVariableContext());
        String logicalPath = variableResolver.resolve(config.getLogicalPath(), context.getVariableContext());

        // TODO: Implement actual path resolution logic
        // This could integrate with VCS systems or storage services
        // For now, simple placeholder implementation
        String physicalPath = resolveLogicalToPhysical(repoId, logicalPath);

        context.setVariable(config.getOutput(), physicalPath);

        Map<String, Object> result = new HashMap<>();
        result.put("repoId", repoId);
        result.put("logicalPath", logicalPath);
        result.put("physicalPath", physicalPath);

        return result;
    }

    private String resolveLogicalToPhysical(String repoId, String logicalPath) {
        // Placeholder implementation
        // Real implementation would query VCS system or storage service
        return "/storage/repos/" + repoId + "/" + logicalPath;
    }
}
