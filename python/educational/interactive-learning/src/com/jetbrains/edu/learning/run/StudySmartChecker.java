package com.jetbrains.edu.learning.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.StudyNames;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyDocumentListener;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class StudySmartChecker {
  private StudySmartChecker() {

  }
  private static final Logger LOG = Logger.getInstance(StudySmartChecker.class);

  public static void smartCheck(@NotNull final AnswerPlaceholder placeholder,
                         @NotNull final Project project,
                         @NotNull final VirtualFile answerFile,
                         @NotNull final TaskFile answerTaskFile,
                         @NotNull final TaskFile usersTaskFile,
                         @NotNull final StudyTestRunner testRunner,
                         @NotNull final VirtualFile virtualFile,
                         @NotNull final Document usersDocument) {

    try {
      final int index = placeholder.getIndex();
      final VirtualFile windowCopy =
        answerFile.copy(project, answerFile.getParent(), answerFile.getNameWithoutExtension() + index + StudyNames.WINDOW_POSTFIX);
      final FileDocumentManager documentManager = FileDocumentManager.getInstance();
      final Document windowDocument = documentManager.getDocument(windowCopy);
      if (windowDocument != null) {
        final File resourceFile = StudyUtils.copyResourceFile(virtualFile.getName(), windowCopy.getName(), project, usersTaskFile.getTask());
        final TaskFile windowTaskFile = new TaskFile();
        TaskFile.copy(answerTaskFile, windowTaskFile);
        StudyDocumentListener listener = new StudyDocumentListener(windowTaskFile);
        windowDocument.addDocumentListener(listener);
        int start = placeholder.getRealStartOffset(windowDocument);
        int end = start + placeholder.getLength();
        final AnswerPlaceholder userAnswerPlaceholder = usersTaskFile.getAnswerPlaceholders().get(placeholder.getIndex());
        int userStart = userAnswerPlaceholder.getRealStartOffset(usersDocument);
        int userEnd = userStart + userAnswerPlaceholder.getLength();
        String text = usersDocument.getText(new TextRange(userStart, userEnd));
        windowDocument.replaceString(start, end, text);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            documentManager.saveDocument(windowDocument);
          }
        });
        VirtualFile fileWindows = StudyUtils.flushWindows(windowTaskFile, windowCopy);
        Process smartTestProcess = testRunner.createCheckProcess(project, windowCopy.getPath());
        final CapturingProcessHandler handler = new CapturingProcessHandler(smartTestProcess);
        final ProcessOutput output = handler.runProcess();
        boolean res = testRunner.getTestsOutput(output).equals(StudyTestRunner.TEST_OK);
        userAnswerPlaceholder.setStatus(res ? StudyStatus.Solved : StudyStatus.Failed);
        StudyUtils.deleteFile(windowCopy);
        if (fileWindows != null) {
          StudyUtils.deleteFile(fileWindows);
        }
        if (!resourceFile.delete()) {
          LOG.error("failed to delete", resourceFile.getPath());
        }
      }
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

}
