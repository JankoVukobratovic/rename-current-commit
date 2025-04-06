package org.jankos;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.GitUtil;
import git4idea.commands.Git;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RenameCurrentCommitAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      messageError("No project. Please open a project first.");
      return;
    }

    List<GitRepository> repos = GitUtil.getRepositoryManager(project).getRepositories();
    if (repos.isEmpty()) {
      messageError("No Git repository found. Please open a Git repository.");
      return;
    }

    GitRepository repo = repos.get(0);
    String newMessage = promptForNewCommitMessage(project);
    if (newMessage == null || newMessage.trim().isEmpty()) {
      messageError("Empty message. Don't do that.");
      return;
    }

    new Task.Backgroundable(project, "Amending git commit") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitLineHandler handler = new GitLineHandler(project, repo.getRoot(), GitCommand.COMMIT);
        handler.addParameters("--amend", "-m", newMessage);
        Git.getInstance().runCommand(handler);

        // update UI
        repo.update();
      }
    }.queue();
  }

  protected String promptForNewCommitMessage(Project project) {
    return Messages.showInputDialog(
        project, "Enter new commit message:", "Rename Commit", Messages.getQuestionIcon());
  }

  private void messageError(String message) {
    Messages.showErrorDialog(message, "Rename Commit - Error");
  }
}
