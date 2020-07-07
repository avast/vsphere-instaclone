package jetbrains.buildServer.agent.impl;

import jetbrains.buildServer.agent.BuildAgent;

public interface BuildAgentEx extends BuildAgent {
    ServerMonitor getServerMonitor();
}
