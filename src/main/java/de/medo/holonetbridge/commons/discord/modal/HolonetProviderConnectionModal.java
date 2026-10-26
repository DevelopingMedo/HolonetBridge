package de.medo.holonetbridge.commons.discord.modal;

import de.medo.holonetbridge.commons.discord.i18n.Texts;
import de.medo.holonetbridge.commons.discord.setup.HolonetSetupDraft;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.modals.Modal;

/**
 * Builds the single Gitea provider-connection modal ({@value #MODAL_ID}): base URL, owner, repository, and
 * token, four fields that fit inside Discord's five-component-per-modal limit without needing a
 * field-count-driven multi-step chain. The {@link #MODAL_ID}/retry-button combination still exists for a
 * different reason: Discord does not allow a modal to be opened directly in response to a modal submission,
 * so a failed validation must reply first and let a button open the (prefilled) retry modal instead.
 */
public final class HolonetProviderConnectionModal {

    /** Custom id of the Gitea provider-connection modal. */
    public static final String MODAL_ID = "setup:modal:provider-gitea";

    public static final String INPUT_BASE_URL = "base_url";
    public static final String INPUT_OWNER = "owner";
    public static final String INPUT_REPOSITORY = "repository";
    public static final String INPUT_TOKEN = "token";

    private HolonetProviderConnectionModal() {}

    /**
     * @param tokenRequired whether the guild has no active connection yet (token mandatory) or an existing
     *     one (token optional - a blank submission keeps the current secret)
     * @param prefill non-secret field values to restore after a failed validation, or {@code null} for a
     *     fresh {@code /setup} invocation; the token field is never prefilled, regardless.
     */
    public static Modal build(boolean tokenRequired, HolonetSetupDraft prefill, Texts texts, DiscordLocale locale) {
        TextInput baseUrl = TextInput.create(INPUT_BASE_URL, TextInputStyle.SHORT)
                .setRequiredRange(1, 200)
                .setPlaceholder("https://gitea.example.com")
                .setValue(prefill == null ? null : prefill.baseUrl())
                .build();
        TextInput owner = TextInput.create(INPUT_OWNER, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .setValue(prefill == null ? null : prefill.owner())
                .build();
        TextInput repository = TextInput.create(INPUT_REPOSITORY, TextInputStyle.SHORT)
                .setRequiredRange(1, 100)
                .setValue(prefill == null ? null : prefill.repository())
                .build();
        TextInput token = TextInput.create(INPUT_TOKEN, TextInputStyle.SHORT)
                .setRequired(tokenRequired)
                .setRequiredRange(tokenRequired ? 1 : 0, 200)
                .setPlaceholder(tokenRequired
                        ? texts.get(locale, "setup.modal.token-required")
                        : texts.get(locale, "setup.modal.token-keep"))
                .build();

        return Modal.create(MODAL_ID, texts.get(locale, "setup.modal.title"))
                .addComponents(
                        Label.of(texts.get(locale, "setup.modal.base-url"), texts.get(locale, "setup.modal.base-url-hint"), baseUrl),
                        Label.of(texts.get(locale, "setup.modal.owner"), texts.get(locale, "setup.modal.owner-hint"), owner),
                        Label.of(texts.get(locale, "setup.modal.repository"), texts.get(locale, "setup.modal.repository-hint"), repository),
                        Label.of(texts.get(locale, "setup.modal.token"), token))
                .build();
    }
}
