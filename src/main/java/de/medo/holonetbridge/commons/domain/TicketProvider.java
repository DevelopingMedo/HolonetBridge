package de.medo.holonetbridge.commons.domain;

public enum TicketProvider {

    GITEA("GITEA", "Gitea"),
    JIRA("JIRA", "Jira"),
    GITHUB("GITHUB", "GitHub"),
    TRELLO("TRELLO", "Trello");

    private final String id;
    private final String displayName;

    TicketProvider(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TicketProvider fromId(String id) {
        for (TicketProvider provider : values()) {
            if (provider.id.equals(id)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown ticket provider ID");
    }

}
