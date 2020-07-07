package jetbrains.buildServer.serverSide;

import org.jetbrains.annotations.NotNull;

public interface BuildAgentEx extends SBuildAgent {
    <T> T getRemoteInterface(@NotNull Class<T> klass);
}
