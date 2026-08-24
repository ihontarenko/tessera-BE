package net.innoventa.tessera.service.file;

import org.jmouse.files.OwnerReference;
import org.jmouse.files.directory.DirectoryPath;

import java.util.List;
import java.util.Optional;

/**
 * Where a file goes in Tessera, and how to read that back off a path.
 *
 * <h2>⚠️ Two trees, and they are different kinds of thing</h2>
 *
 * <p>The library keys {@code storage_directories} by {@code (owner_key, path)} precisely so that a
 * product can run more than one tree, and this one runs exactly two:</p>
 *
 * <ul>
 *   <li>{@link #ATTACHMENTS_ROOT} — <strong>the installation's</strong>, and <strong>machine-made</strong>.
 *       Nobody creates, renames or moves a folder in it: they are minted from an issue's keys as
 *       attachments arrive ({@code issues/<PROJECT>/<ISSUE>}) or from what an agent said a file was about
 *       ({@code ai/mcp/<slug>});</li>
 *   <li>{@link #LIBRARY_ROOT} — <strong>one per member</strong>, and theirs to arrange. Innoventa's file
 *       cabinet is the same shape, for the same reason: a personal tree cannot be a branch of a shared
 *       one without every folder in it needing a grant.</li>
 * </ul>
 *
 * <h2>⚠️ A path is a rendering, never an identity</h2>
 *
 * <p>{@code issues/TSSR/TSSR-42} is built from keys because keys are what a person reads. It follows
 * that the path can go stale — an issue moved between projects keeps its key and its old folder — and
 * that is accepted, because <strong>no authorization is derived from a folder's path except for
 * folders nothing may rename</strong>. See {@code StorageDirectoryAccessTargetResolver}: it reads the
 * project out of a path, and it is only allowed to because the branch below {@link #ISSUES_BRANCH} is
 * machine-made from end to end. The moment a person can rename one of those folders, renaming it moves
 * who may read what.</p>
 *
 * <h2>⚠️ Everything deeper than a root is organisation only</h2>
 *
 * <p>A root's path <em>is</em> the storage namespace of every object filed beneath it, which is why a
 * root can never move. Directories below it contribute nothing to a key — so these branches could be
 * added over a store that has been filling up for months, and not one byte moves.</p>
 */
public final class FileTrees {

    /** Everything the tracker keeps on its own behalf: attachments, and what an agent files. */
    public static final String ATTACHMENTS_ROOT = "tessera/attachments";

    /** One member's own folders. ⚠️ A root per member, all of them at this same path. */
    public static final String LIBRARY_ROOT = "tessera/library";

    /** Under the attachments root: a folder per project, then a folder per issue. */
    public static final String ISSUES_BRANCH = "issues";

    /** Under the attachments root: what an assistant filed. */
    public static final String ASSISTANT_BRANCH = "ai";

    /** Under {@link #ASSISTANT_BRANCH}: what arrived over the Model Context Protocol. */
    public static final String PROTOCOL_BRANCH = "mcp";

    /**
     * The kind of owner a personal tree belongs to.
     *
     * <p>⚠️ Written as {@code MEMBER:<id>} rather than as a bare identifier, because the library never
     * interprets an owner key and a product with two sorts of owner would otherwise have two id spaces
     * quietly sharing one tree.</p>
     */
    public static final String OWNER_MEMBER = "MEMBER";

    private FileTrees() {
    }

    /**
     * The folder an issue's attachments are filed into.
     *
     * @param projectKey the project's key, e.g. {@code TSSR}
     * @param issueKey   the issue's key, e.g. {@code TSSR-42}
     * @return {@code tessera/attachments/issues/<project>/<issue>}
     */
    public static DirectoryPath issueDirectory(String projectKey, String issueKey) {
        return DirectoryPath.of(ATTACHMENTS_ROOT).resolve(ISSUES_BRANCH).resolve(projectKey)
                .resolve(issueKey);
    }

    /**
     * The folder a file that arrived over the protocol with no issue behind it is filed into.
     *
     * @param subject what the agent said the file was about
     * @return {@code tessera/attachments/ai/mcp/<subject>}
     */
    public static DirectoryPath protocolDirectory(String subject) {
        return DirectoryPath.of(ATTACHMENTS_ROOT).resolve(ASSISTANT_BRANCH).resolve(PROTOCOL_BRANCH)
                .resolve(subject);
    }

    /**
     * A member's own tree, as the library spells an owner.
     *
     * @param memberId whose
     * @return the owner reference
     */
    public static OwnerReference member(String memberId) {
        return OwnerReference.of(OWNER_MEMBER, memberId);
    }

    /**
     * The project a folder belongs to, where its path says so.
     *
     * <p>⚠️ Answers the <strong>key</strong>, not an identifier — the path is written from keys and
     * resolving one to a row is a question for whoever has the repository. Empty for every folder
     * outside the issues branch, including the branch's own two top folders: {@code issues} and
     * {@code issues/TSSR} are not <em>in</em> a project, they are the shelf projects sit on.</p>
     *
     * <p>⚠️ It is the <strong>slug</strong> that comes back, since that is what the tree stores —
     * {@code tssr}, not {@code TSSR}. A caller matching it against a project key has to be
     * case-insensitive about it, and the repository query is.</p>
     *
     * @param path the folder's path
     * @return the project key as the path spells it, or empty
     */
    public static Optional<String> projectKeyOf(DirectoryPath path) {
        List<String> segments = path.segments();

        // root (2) + the branch (1) + the project (1) — a folder shallower than that names no project,
        // and one deeper still names the same project as the folder above it.
        if (segments.size() <= DirectoryPath.ROOT_DEPTH + 1 || !inAttachments(path)
                || !ISSUES_BRANCH.equals(segments.get(DirectoryPath.ROOT_DEPTH))) {
            return Optional.empty();
        }

        return Optional.of(segments.get(DirectoryPath.ROOT_DEPTH + 1));
    }

    /**
     * Whether a folder is somewhere in the branch an assistant files into.
     *
     * @param path the folder's path
     * @return {@code true} when it is at or under {@code tessera/attachments/ai}
     */
    public static boolean isAssistantBranch(DirectoryPath path) {
        List<String> segments = path.segments();

        return inAttachments(path) && segments.size() > DirectoryPath.ROOT_DEPTH
                && ASSISTANT_BRANCH.equals(segments.get(DirectoryPath.ROOT_DEPTH));
    }

    /**
     * Whether a folder is the attachments root or somewhere under it.
     *
     * @param path the folder's path
     * @return {@code true} when it is
     */
    public static boolean inAttachments(DirectoryPath path) {
        // ⚠️ Guarded rather than trusted: namespace() reads the first two segments and throws on a path
        // shallower than a root. Nothing in the tree is, but this is asked about paths that arrive from
        // a request, and a malformed one should answer "no" rather than reach a caller as a 500.
        if (path.segments().size() < DirectoryPath.ROOT_DEPTH) {
            return false;
        }

        return ATTACHMENTS_ROOT.equals(path.namespace());
    }
}
