/**
 * Copyright 2014 Microsoft Open Technologies Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoftopentechnologies.intellij.forms;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.microsoftopentechnologies.intellij.helpers.LinkListener;
import com.microsoftopentechnologies.tooling.msservices.components.DefaultLoader;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.AzureCmdException;
import com.microsoftopentechnologies.tooling.msservices.helpers.azure.sdk.StorageClientSDKManagerImpl;
import com.microsoftopentechnologies.tooling.msservices.model.storage.BlobContainer;
import com.microsoftopentechnologies.tooling.msservices.model.storage.ClientStorageAccount;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Calendar;

public class CreateBlobContainerForm extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField nameTextField;
    private JLabel namingGuidelinesLink;

    private Project project;
    private ClientStorageAccount storageAccount;
    private Runnable onCreate;

    private static final String NAME_REGEX = "^[a-z0-9](?!.*--)[a-z0-9-]+[a-z0-9]$";
    private static final int NAME_MAX = 63;
    private static final int NAME_MIN = 3;

    public CreateBlobContainerForm() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        setResizable(false);
        setPreferredSize(new Dimension(412, 170));

        setTitle("Create blob container");
        namingGuidelinesLink.addMouseListener(new LinkListener("http://go.microsoft.com/fwlink/?LinkId=255555"));

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        nameTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                changedName();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                changedName();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                changedName();
            }
        });

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void changedName() {
        buttonOK.setEnabled(nameTextField.getText().length() > 0);
    }

    private void onOK() {
        final String name = nameTextField.getText();

        if (name.length() < NAME_MIN || name.length() > NAME_MAX || !name.matches(NAME_REGEX)) {
            JOptionPane.showMessageDialog(this, "Container names must start with a letter or number, and can contain only letters, numbers, and the dash (-) character.\n" +
                    "Every dash (-) character must be immediately preceded and followed by a letter or number; consecutive dashes are not permitted in container names.\n" +
                    "All letters in a container name must be lowercase.\n" +
                    "Container names must be from 3 through 63 characters long.", "Service Explorer", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Creating blob container...", false) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                try {
                    progressIndicator.setIndeterminate(true);

                    for (BlobContainer blobContainer : StorageClientSDKManagerImpl.getManager().getBlobContainers(storageAccount)) {
                        if (blobContainer.getName().equals(name)) {
                            ApplicationManager.getApplication().invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    JOptionPane.showMessageDialog(null, "A blob container with the specified name already exists.", "Service Explorer", JOptionPane.ERROR_MESSAGE);
                                }
                            });

                            return;
                        }
                    }

                    BlobContainer blobContainer = new BlobContainer(name, storageAccount.getBlobsUri() + name, "", Calendar.getInstance(), "");
                    StorageClientSDKManagerImpl.getManager().createBlobContainer(storageAccount, blobContainer);

                    if (onCreate != null) {
                        ApplicationManager.getApplication().invokeLater(onCreate);
                    }
                } catch (AzureCmdException e) {
                    DefaultLoader.getUIHelper().showException("Error creating blob container", e, "Error creating blob container", false, true);
                }
            }
        });

        dispose();
    }

    private void onCancel() {
        dispose();
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public void setStorageAccount(ClientStorageAccount storageAccount) {
        this.storageAccount = storageAccount;
    }

    public void setOnCreate(Runnable onCreate) {
        this.onCreate = onCreate;
    }
}