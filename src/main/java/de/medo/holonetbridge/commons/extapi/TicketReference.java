package de.medo.holonetbridge.commons.extapi;

/** Secret-free identity; issue numbers alone are not stable across repositories or transfers. */
public record TicketReference(
        String provider,
        String baseUrl,
        String owner,
        String repository,
        Long repositoryId,
        Long issueId,
        Long number) {
    public TicketReference {
        if (!"gitea".equals(provider) || baseUrl == null || owner == null || repository == null
                || repositoryId == null || repositoryId <= 0 || issueId == null || issueId <= 0
                || number == null || number <= 0) {
            throw new IllegalArgumentException("Invalid ticket identity");
        }
    }
}
