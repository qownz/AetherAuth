package com.aetherauth.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.aetherauth.api.DialogBridge;
import com.aetherauth.api.DialogOutcome;
import com.aetherauth.api.DialogRequest;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Paper 1.21.6+ dialog based authentication screen.
 *
 * <p>This class is compiled for Java 21 and is only ever loaded when the
 * server exposes the dialog API. All text is rendered with MiniMessage, which
 * gives us gradients, hex colours and the same theme as the rest of the
 * plugin.</p>
 */
public final class PaperDialogBridge implements DialogBridge {

    private static final String EMAIL_KEY = "aether_email";
    private static final String PASSWORD_KEY = "aether_password";
    private static final String CONFIRM_KEY = "aether_confirm";
    private static final String VALUE_KEY = "aether_value";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public String name() {
        return "Paper Dialog API";
    }

    @Override
    public boolean available() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void show(final Player player, final DialogRequest request, final Consumer<DialogOutcome> callback) {
        final AtomicBoolean completed = new AtomicBoolean(false);

        final List<DialogInput> inputs = new ArrayList<DialogInput>(3);
        if (request.emailField()) {
            inputs.add(DialogInput.text(EMAIL_KEY, component(request.emailLabel()))
                    .width(clamp(request.width(), 100, 400))
                    .labelVisible(true)
                    .initial(request.initialEmail() == null ? "" : request.initialEmail())
                    .maxLength(clamp(request.maxEmailLength(), 3, 254))
                    .build());
        }
        if (request.passwordField()) {
            inputs.add(DialogInput.text(PASSWORD_KEY, component(request.passwordLabel()))
                    .width(clamp(request.width(), 100, 400))
                    .labelVisible(true)
                    .initial(request.initialPassword() == null ? "" : request.initialPassword())
                    .maxLength(clamp(request.maxPasswordLength(), 1, 128))
                    .build());
        }
        if (request.confirmField()) {
            inputs.add(DialogInput.text(CONFIRM_KEY, component(request.confirmLabel()))
                    .width(clamp(request.width(), 100, 400))
                    .labelVisible(true)
                    .maxLength(clamp(request.maxPasswordLength(), 1, 128))
                    .build());
        }
        if (request.valueField()) {
            inputs.add(DialogInput.text(VALUE_KEY, component(request.valueLabel()))
                    .width(clamp(request.width(), 100, 400))
                    .labelVisible(true)
                    .initial(request.initialValue() == null ? "" : request.initialValue())
                    .maxLength(clamp(request.maxValueLength(), 1, 254))
                    .build());
        }

        final List<DialogBody> body = new ArrayList<DialogBody>(request.body().size());
        for (String line : request.body()) {
            body.add(DialogBody.plainMessage(component(line), clamp(request.width(), 100, 400)));
        }

        final ActionButton submit = ActionButton.builder(component(request.submitLabel()))
                .width(clamp(request.buttonWidth(), 40, 400))
                .action(DialogAction.customClick(new DialogActionCallback() {
                    @Override
                    public void accept(DialogResponseView response, Audience audience) {
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        callback.accept(DialogOutcome.submit(
                                read(response, EMAIL_KEY),
                                read(response, PASSWORD_KEY),
                                read(response, CONFIRM_KEY),
                                read(response, VALUE_KEY)));
                    }
                }, ClickCallback.Options.builder().uses(1).build()))
                .build();

        final ActionButton cancel = ActionButton.builder(component(request.cancelLabel()))
                .width(clamp(request.buttonWidth(), 40, 400))
                .action(DialogAction.customClick(new DialogActionCallback() {
                    @Override
                    public void accept(DialogResponseView response, Audience audience) {
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        try {
                            audience.closeDialog();
                        } catch (Throwable ignored) {
                            // The client already closed the dialog.
                        }
                        callback.accept(DialogOutcome.cancel());
                    }
                }, ClickCallback.Options.builder().uses(1).build()))
                .build();

        final Component title = component(request.title());

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(request.canClose())
                        .pause(false)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(submit, cancel)));

        player.showDialog(dialog);
    }

    @Override
    public void close(Player player) {
        try {
            player.closeDialog();
        } catch (Throwable ignored) {
        }
    }

    private Component component(String input) {
        return miniMessage.deserialize(input == null ? "" : input);
    }

    private static String read(DialogResponseView response, String key) {
        try {
            String value = response.getText(key);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
