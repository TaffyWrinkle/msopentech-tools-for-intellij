/**
 * Copyright 2014 Microsoft Open Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.microsoftopentechnologies.intellij.helpers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ServiceCodeReferenceHelper {
    private static final String AZURESDK_URL = "http://zumo.blob.core.windows.net/sdk/azuresdk-android-1.1.5.zip";

    private static final String TEMPLATES_URL = "/com/microsoftopentechnologies/intellij/templates/";

    private static final String NOTIFICATIONHELPER_PATH = "notifications/";
    private static final String NOTIFICATIONHUBS_PATH = "notificationhubs/";
    private static final String NOTIFICATIONHUBS_LIBTEMPLATE = "notification.xml";
    private static final String NOTIFICATIONHUBS_LIBNAME = "notification";

    private static final String STRINGS_XML = "src/main/res/values/strings.xml";

    @NotNull
    private final Project project;

    @NotNull
    private final Module module;
    private String sourcePath;

    public ServiceCodeReferenceHelper(@NotNull Project project, @NotNull Module module) {
        this.project = project;
        this.module = module;
    }

    public static InputStream getTemplateResource(String libTemplate) {
        return ServiceCodeReferenceHelper.class.getResourceAsStream(TEMPLATES_URL + libTemplate);
    }

    public void addNotificationHubsLibs()
            throws ParserConfigurationException, TransformerException, SAXException, XPathExpressionException, IOException {
        addReferences(NOTIFICATIONHUBS_PATH, NOTIFICATIONHUBS_LIBTEMPLATE, NOTIFICATIONHUBS_LIBNAME);
    }

    public void fillMobileServiceResource(String activityName, String appUrl, String appKey) throws IOException {
        if (module.getModuleFile() != null && module.getModuleFile().getParent() != null) {
            VirtualFile vf = module.getModuleFile().getParent().findFileByRelativePath(STRINGS_XML);

            if (vf != null) {
                FileDocumentManager fdm = FileDocumentManager.getInstance();
                com.intellij.openapi.editor.Document document = fdm.getDocument(vf);

                if (document != null) {
                    String content = document.getText();
                    content = content.replace(">$APPURL_" + activityName + "<", ">" + appUrl + "<");
                    content = content.replace(">$APPKEY_" + activityName + "<", ">" + appKey + "<");
                    document.setText(content);
                    fdm.saveDocument(document);
                }
            }
        }
    }

    public void fillNotificationHubResource(String activityName, String senderId, String connStr, String hubName) {
        if (module.getModuleFile() != null && module.getModuleFile().getParent() != null) {
            VirtualFile vf = module.getModuleFile().getParent().findFileByRelativePath(STRINGS_XML);

            if (vf != null) {
                FileDocumentManager fdm = FileDocumentManager.getInstance();
                com.intellij.openapi.editor.Document document = fdm.getDocument(vf);

                if (document != null) {
                    String content = document.getText();
                    content = content.replace(">$SENDERID_" + activityName + "<", ">" + senderId + "<");
                    content = content.replace(">$CONNSTR_" + activityName + "<", ">" + connStr + "<");
                    content = content.replace(">$HUBNAME_" + activityName + "<", ">" + hubName + "<");
                    document.setText(content);
                    fdm.saveDocument(document);
                }
            }
        }
    }

    public void fillOffice365Resource(String activityName, String appId, String name) {
        if (module.getModuleFile() != null && module.getModuleFile().getParent() != null) {
            VirtualFile vf = module.getModuleFile().getParent().findFileByRelativePath(STRINGS_XML);

            if (vf != null) {
                FileDocumentManager fdm = FileDocumentManager.getInstance();
                com.intellij.openapi.editor.Document document = fdm.getDocument(vf);

                if (document != null) {
                    String content = document.getText();
                    content = content.replace(">$O365_APP_ID_" + activityName + "<", ">" + appId + "<");
                    content = content.replace(">$O365_NAME_" + activityName + "<", ">" + name + "<");
                    document.setText(content);
                    fdm.saveDocument(document);
                }
            }
        }
    }

    public static Boolean isAndroidGradleModule(VirtualFile virtualFileDir) throws IOException {
        for (VirtualFile file : virtualFileDir.getChildren()) {
            if (file.getName().contains("build.gradle")) {
                if(getStringAndCloseStream(file.getInputStream()).contains("com.android.tools.build"))
                    return true;
            }
        }

        return false;
    }

    private void addReferences(String zipPath, String libTemplate, String libName)
            throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, TransformerException {
        //Downloads libraries
        String path = System.getProperty("java.io.tmpdir");

        if (!path.endsWith(File.separator)) {
            path = path + File.separator;
        }

        path = path + "TempAzure";

        File pathFile = new File(path);

        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }

        path = path + File.separator + "androidAzureSDK.zip";

        File zipFile = new File(path);

        if (!zipFile.exists()) {
            saveUrl(path, AZURESDK_URL);
        }

        final VirtualFile moduleFile = module.getModuleFile();

        if (moduleFile != null) {
            moduleFile.refresh(false, false);

            final VirtualFile moduleDir = module.getModuleFile().getParent();

            if (moduleDir != null) {
                moduleDir.refresh(false, false);

                if (isAndroidGradleModule(moduleDir)) {
                    copyJarFiles(moduleDir, zipFile, zipPath);

                    if (zipPath.equals(NOTIFICATIONHUBS_PATH)) {
                        copyJarFiles(moduleDir, zipFile, NOTIFICATIONHELPER_PATH);
                    }

                    //hardcoded path for gradle project
                    sourcePath = moduleDir.getUrl() + "/src/main/java";
                } else {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(moduleFile.getInputStream());

                    final XPathFactory xPathfactory = XPathFactory.newInstance();

                    XPath isAndroidModuleXpath = xPathfactory.newXPath();
                    XPathExpression isAndroidModuleQuery = isAndroidModuleXpath.compile("boolean(//facet[@type='android'])");
                    Boolean isAndroidModule = (Boolean) isAndroidModuleQuery.evaluate(doc, XPathConstants.BOOLEAN);

                    if (isAndroidModule) {
                        //Unzips libraries and copies them to libs folder
                        copyJarFiles(project.getBaseDir(), zipFile, zipPath);

                        if (zipPath.equals(NOTIFICATIONHUBS_PATH)) {
                            copyJarFiles(project.getBaseDir(), zipFile, NOTIFICATIONHELPER_PATH);
                        }

                        //Add project level reference
                        VirtualFile ideaFolder = project.getProjectFile().getParent();
                        VirtualFile librariesFolder = null;

                        for (VirtualFile vf : ideaFolder.getChildren()) {
                            if (vf.getName().equals("libraries")) {
                                librariesFolder = vf;
                            }
                        }

                        if (librariesFolder == null) {
                            librariesFolder = ideaFolder.createChildDirectory(project, "libraries");
                        }

                        final VirtualFile mobileServiceRefFile = librariesFolder.createChildData(project, libTemplate);

                        final String template = getStringAndCloseStream(getTemplateResource(libTemplate));
                        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                            @Override
                            public void run() {
                                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            mobileServiceRefFile.setBinaryContent(template.getBytes());
                                        } catch (Throwable ex) {
                                            UIHelper.showException("Error trying to configure Azure Mobile Services", ex);
                                        }
                                    }
                                });
                            }
                        }, ModalityState.defaultModalityState());

                        //Sets the module main source path
                        XPath xpathSources = xPathfactory.newXPath();
                        XPathExpression sourcesQuery = xpathSources.compile("//sourceFolder");
                        NodeList sources = ((org.w3c.dom.NodeList) sourcesQuery.evaluate(doc, XPathConstants.NODESET));

                        for (int i = 0; i < sources.getLength() && sourcePath == null; i++) {
                            String url = sources.item(i).getAttributes().getNamedItem("url").getNodeValue();
                            if (url.contains("src")) {
                                sourcePath = url.replace("file://$MODULE_DIR$", moduleFile.getParent().getUrl());
                            }
                        }

                        //Adds the libraries
                        XPath xpathComponent = xPathfactory.newXPath();
                        XPathExpression componentQuery = xpathComponent.compile("//component[@name='NewModuleRootManager']");
                        Node component = ((org.w3c.dom.NodeList) componentQuery.evaluate(doc, XPathConstants.NODESET)).item(0);

                        XPath existsLibEntryXPath = xPathfactory.newXPath();
                        XPathExpression existsLibEntryQuery = existsLibEntryXPath.compile("boolean(//orderEntry[@name='" + libName + "' and @type='library'])");
                        Boolean existsLibEntry = (Boolean) existsLibEntryQuery.evaluate(doc, XPathConstants.BOOLEAN);

                        if (!existsLibEntry) {
                            Element orderEntry = doc.createElement("orderEntry");
                            orderEntry.setAttribute("type", "library");
                            orderEntry.setAttribute("name", libName);
                            orderEntry.setAttribute("level", "project");

                            component.appendChild(orderEntry);

                            // Use a Transformer for output
                            TransformerFactory tFactory = TransformerFactory.newInstance();
                            Transformer transformer = tFactory.newTransformer();
                            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

                            DOMSource source = new DOMSource(doc);
                            StringWriter writer = new StringWriter();
                            StreamResult result = new StreamResult(writer);
                            transformer.transform(source, result);
                            final byte[] buff = writer.getBuffer().toString().getBytes();
                            moduleFile.setWritable(true);
                            ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                                @Override
                                public void run() {
                                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                moduleFile.setBinaryContent(buff);
                                            } catch (Throwable ex) {
                                                UIHelper.showException("Error trying to configure Azure Mobile Services", ex);
                                            }
                                        }
                                    });
                                }
                            }, ModalityState.defaultModalityState());
                        }
                    }
                }
            }
        }
    }

    private void copyJarFiles(VirtualFile baseDir, File zipFile, String zipPath) throws IOException {
        if (baseDir.isDirectory()) {
            final ZipFile zip = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                final ZipEntry zipEntry = entries.nextElement();

                if (!zipEntry.isDirectory() && zipEntry.getName().startsWith(zipPath) &&
                        zipEntry.getName().endsWith(".jar") &&
                        !(zipEntry.getName().endsWith("-sources.jar") || zipEntry.getName().endsWith("-javadoc.jar"))) {
                    VirtualFile libsVf = null;

                    for (VirtualFile vf : baseDir.getChildren()) {
                        if (vf.getName().equals("libs")) {
                            libsVf = vf;
                            break;
                        }
                    }

                    if (libsVf == null) {
                        libsVf = baseDir.createChildDirectory(project, "libs");
                    }

                    final VirtualFile libs = libsVf;

                    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        InputStream mobileserviceInputStream = zip.getInputStream(zipEntry);
                                        VirtualFile msVF = libs.createChildData(project, zipEntry.getName().split("/")[1]);
                                        msVF.setBinaryContent(getArray(mobileserviceInputStream));
                                    } catch (Throwable ex) {
                                        UIHelper.showException("Error trying to configure Azure Mobile Services", ex);
                                    }
                                }
                            });
                        }
                    }, ModalityState.defaultModalityState());
                }
            }
        }
    }

    private static void saveUrl(String filename, String urlString)
            throws IOException {
        InputStream in = null;
        FileOutputStream fout = null;

        try {
            in = new URL(urlString).openStream();
            fout = new FileOutputStream(filename);

            byte data[] = new byte[1024];
            int count;

            while ((count = in.read(data, 0, 1024)) != -1) {
                fout.write(data, 0, count);
            }
        } finally {
            if (in != null) {
                in.close();
            }

            if (fout != null) {
                fout.close();
            }
        }
    }

    private byte[] getArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();

        return buffer.toByteArray();
    }

    public static String getStringAndCloseStream(InputStream is) throws IOException {
        //Using the trick described in this link to read whole streams in one operation:
        //http://stackoverflow.com/a/5445161
        try {
            Scanner s = new Scanner(is).useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
        finally {
            is.close();
        }
    }

    @NotNull
    public static String getStringAndCloseStream(@NotNull InputStream is, @NotNull String charsetName) throws IOException {
        //Using the trick described in this link to read whole streams in one operation:
        //http://stackoverflow.com/a/5445161
        try {
            Scanner s = new Scanner(is, charsetName).useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
        finally {
            is.close();
        }
    }
}