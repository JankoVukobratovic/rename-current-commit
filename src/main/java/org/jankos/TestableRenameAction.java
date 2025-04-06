package org.jankos;

import com.intellij.openapi.project.Project;

public class TestableRenameAction extends RenameCurrentCommitAction {
  private final String testMessage;

  public TestableRenameAction(String testMessage) {
    this.testMessage = testMessage;
  }

  @Override
  protected String promptForNewCommitMessage(Project project) {
    return testMessage;
  }
}
