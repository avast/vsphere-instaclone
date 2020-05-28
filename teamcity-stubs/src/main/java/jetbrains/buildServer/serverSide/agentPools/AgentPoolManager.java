package jetbrains.buildServer.serverSide.agentPools;

import org.jetbrains.annotations.NotNull;
import java.util.List;

public interface AgentPoolManager {
    @NotNull
    List<AgentPool> getAllAgentPools();
}
