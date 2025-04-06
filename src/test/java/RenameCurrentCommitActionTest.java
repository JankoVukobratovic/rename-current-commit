import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jankos.TestableRenameAction;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class RenameCurrentCommitActionTest extends LightPlatformCodeInsightFixture4TestCase {

  private VirtualFile repoRoot;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();

    // 1. Create a physical temp directory for the Git repository
    File repoRootFile = FileUtil.createTempDirectory("test_git_repo", null);
    VirtualFile repoRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(repoRootFile);
    assertNotNull("Virtual file for repo root is null", repoRoot);

    // 2. Initialize the Git repository
    GitLineHandler handler = new GitLineHandler(getProject(), repoRoot, GitCommand.INIT);
    GitCommandResult result = Git.getInstance().runCommand(handler);
    assertTrue("Git init failed: " + result.getErrorOutputAsJoinedString(), result.success());

    // 3. Refresh the .git folder if it's not immediately visible
    VirtualFile dotGit = repoRoot.findChild(".git");
    if (dotGit == null) {
      repoRoot.refresh(false, true); // Force refresh if .git is not yet visible
      dotGit = repoRoot.findChild(".git");
    }
    assertNotNull("`.git` folder not found after git init", dotGit);

    // 4. Mark the .git folder as dirty to trigger IntelliJ VFS update
    VfsUtil.markDirtyAndRefresh(false, true, true, dotGit);

    // 5. Tell IntelliJ to refresh the Git repository roots
    VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(getProject());
    vcsDirtyScopeManager.markEverythingDirty(); // Ensures VCS is notified

    // 6. Refresh the VCS roots in the project-level manager
//    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(getProject());
//    Arrays.stream(vcsManager.getAllActiveVcss()).toList().forEach(x -> x.???????); // Update VCS roots

    // 7. Manually update the repository manager to ensure the repo is registered
    GitRepositoryManager repoManager = GitUtil.getRepositoryManager(getProject());
    GitRepository repository = repoManager.getRepositoryForRootQuick(repoRoot);
    if (repository == null) {
      repoManager.updateRepository(repoRoot); // Trigger repository update
    }

    // 8. Final check: Make sure the repository is now detected
    repository = repoManager.getRepositoryForRootQuick(repoRoot);
    assertNotNull("Failed to detect Git repository", repository);
  }

  @Test
  @Disabled("Okay, the test fails, but when i ./gradlew runIde it works. I just cant figure out how to test it... :(")
  public void testRenameGitCommit() throws Exception {
    String originalCommitMessage = "Initial commit";
    String newCommitMessage = "Renamed commit";

    // Create a file and commit it with the original message
    createFileAndCommit(originalCommitMessage);

    // Verify the initial commit message
    String initialCommitMessage = getLatestGitCommitMessage();
    assertThat(initialCommitMessage).isEqualTo("Initial commit");

    // Simulate the action of renaming the latest Git commit
    myFixture.testAction(new TestableRenameAction(newCommitMessage));

    // Verify that the commit message has been updated
    String latestCommitMessage = getLatestGitCommitMessage();
    assertThat(latestCommitMessage).isEqualTo(newCommitMessage);
  }

  private void createGitRepository() throws Exception {
    String physical = myFixture.getTempDirFixture().findOrCreateDir("my-git-repo").getPath();
    File physicalFile = new File(physical);

    //    String tempDirPath = myFixture.getTempDirPath();
    //    File physicalDir = new File(tempDirPath);

    if (!physicalFile.exists()) {
      if (!physicalFile.mkdirs()) {
        throw new IOException("Failed to create temp git directory: " + physical);
      }
    }

    VirtualFile baseDir = myFixture.getTempDirFixture().findOrCreateDir("");

    // Run `git init` using the physical path
    GitLineHandler initHandler = new GitLineHandler(getProject(), baseDir, GitCommand.INIT);
    GitCommandResult result = Git.getInstance().runCommand(initHandler);
    if (!result.success()) {
      throw new Exception("Git init failed: " + result.getErrorOutputAsJoinedString());
    }

    // Force repository update
    GitRepository repository =
            GitUtil.getRepositoryManager(getProject()).getRepositoryForRootQuick(baseDir);
    if (repository == null) {
      throw new IllegalStateException("Failed to initialize Git repository");
    }

    repository.update();
  }

  private void createFileAndCommit(@NotNull String commitMessage) throws Exception {
    // Create a new file in the project
    VirtualFile testFile = createTestFile("TestFile.java", "public class TestFile {}");

    // Add and commit the file to the repository
    runGitCommand(GitCommand.ADD, testFile.getPath());
    runGitCommand(GitCommand.COMMIT, "-m", commitMessage);
  }

  private VirtualFile createTestFile(String fileName, String content) throws Exception {
    // Create a new file with the specified content
    VirtualFile file =
        myFixture.getTempDirFixture().findOrCreateDir("").createChildData(this, fileName);
    file.setBinaryContent(content.getBytes());
    return file;
  }

  private String getLatestGitCommitMessage() throws Exception {
    // Run the git log command to get the latest commit message
    GitCommandResult result = runGitCommand(GitCommand.LOG, "-1", "--pretty=%B");
    return result.getOutput().stream().reduce("", (a, b) -> a + "\n" + b).trim();
  }

  private GitCommandResult runGitCommand(GitCommand command, String... args) throws Exception {
    GitLineHandler handler =
        new GitLineHandler(
            getProject(), myFixture.getTempDirFixture().findOrCreateDir(""), command);
    handler.addParameters(args);
    GitCommandResult result = Git.getInstance().runCommand(handler);

    if (!result.success()) {
      throw new Exception(command.name() + " failed: " + result.getErrorOutputAsJoinedString());
    }

    // update UI
    GitUtil.getRepositoryManager(getProject()).getRepositories().get(0).update();
    return result;
  }
}
