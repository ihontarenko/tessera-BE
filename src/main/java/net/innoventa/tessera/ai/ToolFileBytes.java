package net.innoventa.tessera.ai;

import org.jmouse.ai.ToolInvocation;
import org.jmouse.ai.RefusalReason;
import org.jmouse.ai.ToolRefusedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * How a file arrives over the protocol — the two forms, and the one that is a capability.
 *
 * <h2>⚠️ Extracted the moment a second action carried bytes (TSSR-0105)</h2>
 *
 * <p>{@code issues_attach} was the only action with a file in it, so this lived inside
 * {@link IssueTool}. {@code files_upload} is the second, and the two must not drift: a copy would mean
 * two answers to <em>may this server read that path</em>, and the looser of the two is the one that
 * matters.</p>
 *
 * <h2>⚠️ Reading the server's disk is off until somebody names the directory</h2>
 *
 * <p>{@code path} is a capability rather than a convenience — it is this installation reading its own
 * filesystem on a caller's word. Unset, the form is refused outright and says so; set, the path is
 * resolved and compared against the resolved root, so {@code ../} climbs out of nothing.</p>
 */
@Component
public class ToolFileBytes {

    /**
     * The one directory an action may read a file from, or blank to refuse the form entirely.
     */
    @Value("${tessera.protocol.upload-root:}")
    private String uploadRoot;

    /**
     * The bytes an invocation is carrying, whichever way it sent them.
     *
     * @param invocation the call
     * @return the bytes
     */
    public byte[] of(ToolInvocation invocation) {
        String encoded = invocation.optionalString("base64").orElse(null);
        String path    = invocation.optionalString("path").orElse(null);

        boolean hasEncoded = encoded != null && !encoded.isBlank();
        boolean hasPath    = path != null && !path.isBlank();

        if (hasEncoded == hasPath) {
            throw new ToolRefusedException(RefusalReason.INVALID_ARGUMENT,
                    "Send the file one way: 'base64' with the bytes in it, or 'path' pointing at a local "
                    + "file. Neither was given, or both were.");
        }

        if (hasEncoded) {
            try {
                return Base64.getDecoder().decode(encoded.trim());
            } catch (IllegalArgumentException malformed) {
                throw new ToolRefusedException(RefusalReason.UNPARSEABLE_VALUE,
                        "'base64' is not base64. Send the file's bytes encoded, with no data: prefix.");
            }
        }

        return readFromDisk(path.trim());
    }

    private byte[] readFromDisk(String path) {
        if (uploadRoot == null || uploadRoot.isBlank()) {
            throw new ToolRefusedException(RefusalReason.MISSING_PERMISSION,
                    "This installation does not read files from disk. Send the bytes as 'base64' instead "
                    + "— or set 'tessera.protocol.upload-root' to the one directory uploads may come from.");
        }

        Path root   = Path.of(uploadRoot).toAbsolutePath().normalize();
        Path target = Path.of(path).toAbsolutePath().normalize();

        if (!target.startsWith(root)) {
            throw new ToolRefusedException(RefusalReason.MISSING_PERMISSION,
                    "That path is outside the directory this installation reads uploads from (%s)."
                            .formatted(root));
        }

        if (!Files.isRegularFile(target)) {
            throw new ToolRefusedException(RefusalReason.NOTHING_TO_ACT_ON,
                    "There is no file at " + target + ".");
        }

        try {
            return Files.readAllBytes(target);
        } catch (IOException unreadable) {
            throw new ToolRefusedException(RefusalReason.NOTHING_TO_ACT_ON,
                    "That file could not be read: " + unreadable.getMessage());
        }
    }
}
