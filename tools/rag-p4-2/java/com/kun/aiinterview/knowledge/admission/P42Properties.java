package com.kun.aiinterview.knowledge.admission;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.experimental-evidence")
public class P42Properties {
    private boolean enabled = false;
    private String rerankWorkspaceId;
    private String rerankApiKey;
    private String gatePromptPath;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRerankWorkspaceId() { return rerankWorkspaceId; }
    public void setRerankWorkspaceId(String value) { rerankWorkspaceId = value; }
    public String getRerankApiKey() { return rerankApiKey; }
    public void setRerankApiKey(String value) { rerankApiKey = value; }
    public String getGatePromptPath() { return gatePromptPath; }
    public void setGatePromptPath(String value) { gatePromptPath = value; }
    @Override public String toString() { return "P42Properties[enabled=" + enabled + ", credentials=REDACTED]"; }
}
