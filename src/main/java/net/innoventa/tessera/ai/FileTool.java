package net.innoventa.tessera.ai;

import lombok.RequiredArgsConstructor;
import net.innoventa.tessera.service.file.AssistantFiles;
import net.innoventa.tessera.service.file.FileTrees;
import org.jmouse.ai.ArgumentSchema;
import org.jmouse.ai.CallerAttributes;
import org.jmouse.ai.ToolAction;
import org.jmouse.ai.ToolDefinition;
import org.jmouse.ai.ToolInvocation;
import org.jmouse.files.OwnerReference;
import org.jmouse.files.jpa.ManagedFile;

import org.jmouse.files.jpa.directory.StorageDirectory;
import org.jmouse.files.management.FileManagement;
import org.jmouse.storage.Content;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeping a file that has no issue to hang it on.
 *
 * <h2>Why this exists beside {@code issues_attach}</h2>
 *
 * <p>{@code issues_attach} requires an {@code issueKey}, so until now a file that was not an attachment
 * could not exist in Tessera at all. The case it leaves out is an ordinary one: an assistant is shown a
 * screenshot of something wrong <em>before</em> there is a ticket for it, and has nowhere to put it —
 * so it either invents a ticket to hold the file or loses the file.</p>
 *
 * <p>⚠️ <strong>Not a second upload path.</strong> It goes through {@code FileManagement} exactly as the
 * screen and {@code issues_attach} do, so the acceptance policy, the size ceiling, the digest and the
 * registry row are the ones already there. What differs is only what the file is filed against.</p>
 *
 * <h2>⚠️ The folder is named by the caller, and that is the point of it</h2>
 *
 * <p>{@code subject} is a short phrase saying what the file is about —
 * <em>"modal window does not fit on display"</em> — which becomes
 * {@code tessera/attachments/ai/mcp/modal-window-does-not-fit-on-display}. A tree of timestamps would be
 * a tree nobody opens; a tree of sentences is one somebody can scan. The slug is the library's
 * ({@code DirectorySlugs}), so what a caller sends is a name rather than a path, and it cannot climb
 * anywhere.</p>
 *
 * <h2>⚠️ This branch belongs to no project, so a GLOBAL grant is what reaches it</h2>
 *
 * <p>Every other file in this product resolves to the project of the issue it hangs on. A file with no
 * issue has no project to derive a place from, and inventing one would be inventing a place a grant was
 * never written about — so {@code ai/mcp/…} resolves to the installation and only {@code file:write}
 * held at {@code @GLOBAL} may write here. Said plainly: <strong>an ordinary member with
 * {@code file:read} on their projects does not see this branch</strong>, and whoever needs to is given
 * the permission globally, on purpose.</p>
 *
 * <p>⚠️ Which is also why the action is <strong>not scope-confined</strong>. There is no project to
 * confine it to, and naming one would suggest the file lands somewhere it does not.</p>
 */
@Component
@RequiredArgsConstructor
public class FileTool implements ToolDefinition {

    private final FileManagement     files;
    private final AssistantFiles     assistantFiles;
    private final ToolMembers        members;
    private final ToolFileBytes      fileBytes;

    @Override
    public String toolName() {
        return "files";
    }

    @Override
    public List<ToolAction> actions() {
        return List.of(upload());
    }

    private ToolAction upload() {
        return ToolAction.builder()
                .toolName(toolName())
                .name("upload")
                .title("Keep a file that is not on an issue")
                .description("Stores a file under a folder named for what it is about — a screenshot "
                           + "taken before there is a ticket for it, a log, an export. Use "
                           + "issues_attach instead whenever there IS an issue: a file on the issue is "
                           + "where somebody will look for it. Send the bytes as 'base64', or 'path' to "
                           + "read one off this server's disk where the installation allows that.")
                .inputSchema(ArgumentSchema.builder()
                        .requiredString("name", "What to call it — the filename, including its extension.")
                        .requiredString("subject", "A short phrase saying what this file is about, which "
                                                 + "becomes the folder it is filed in, e.g. 'modal window "
                                                 + "does not fit on display'. Reuse the same phrase to put "
                                                 + "several files together.")
                        .optionalString("base64", "The bytes, base64-encoded. Give this or 'path', never both.")
                        .optionalString("path", "A local file to read instead of sending its bytes. Only "
                                              + "paths under this installation's configured upload root "
                                              + "are allowed, and the form is refused entirely when none "
                                              + "is configured.")
                        .optionalString("contentType", "The media type, when it is not obvious from the name."))
                .handler(this::handleUpload)
                .build();
    }

    private Object handleUpload(ToolInvocation invocation) {
        String name    = invocation.requiredString("name");
        String subject = invocation.requiredString("subject");
        byte[] bytes   = fileBytes.of(invocation);

        // ⚠️ Through a transactional bean rather than the tree directly — a tool handler runs in no
        // transaction of its own, and making a folder renumbers the nested set. See AssistantFiles.
        StorageDirectory folder = assistantFiles.folderFor(subject);

        // ⚠️ THE UPLOADER IS THE AGENT, NOT ITS OWNER — the rule V000034 settled for comments and
        // `issues_attach` follows: a file an assistant kept, hung on the person who happens to own it,
        // makes a tree full of work nobody remembers doing look like a tree somebody was busy in.
        String uploader = Optional
                .ofNullable(invocation.caller().attributes().get(CallerAttributes.AGENT_ID))
                .filter(agentId -> !agentId.isBlank())
                .orElseGet(() -> members.actingSubject(invocation).getId());

        ManagedFile stored = files.upload(
                OwnerReference.of(OwnerReference.DIRECTORY, folder.getId()),
                FileTrees.ATTACHMENTS_ROOT,
                Content.of(name, invocation.optionalString("contentType").orElse(null), bytes.length,
                           () -> new ByteArrayInputStream(bytes)),
                name,
                uploader);

        Map<String, Object> answer = new LinkedHashMap<>();

        answer.put("id",          stored.getId());
        answer.put("name",        stored.getDisplayName());
        answer.put("contentType", stored.getStoredFile().getContentType().toString());
        answer.put("sizeBytes",   stored.getStoredFile().getSizeBytes());
        answer.put("folder",      folder.getPath());
        answer.put("kept",        true);

        return answer;
    }
}
