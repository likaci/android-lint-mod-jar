//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.android.tools.lint.detector.api;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.OutputFile.FilterType;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.client.api.CircularDependencyException;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.SdkInfo;
import com.google.common.annotations.Beta;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Beta
public class Project {
    private static Boolean sAospBuild;
    private static AndroidVersion sCurrentVersion;
    protected final LintClient client;
    protected final File dir;
    protected final File referenceDir;
    protected Configuration configuration;
    protected String pkg;
    protected int buildSdk = -1;
    protected IAndroidTarget target;
    protected AndroidVersion manifestMinSdk;
    protected AndroidVersion manifestTargetSdk;
    protected boolean library;
    protected String name;
    protected String proguardPath;
    protected boolean mergeManifests;
    protected SdkInfo sdkInfo;
    protected List<File> files;
    protected List<File> proguardFiles;
    protected List<File> gradleFiles;
    protected List<File> manifestFiles;
    protected List<File> javaSourceFolders;
    protected List<File> javaClassFolders;
    protected List<File> nonProvidedJavaLibraries;
    protected List<File> javaLibraries;
    protected List<File> testSourceFolders;
    protected List<File> testLibraries;
    protected List<File> resourceFolders;
    protected List<File> assetFolders;
    protected List<Project> directLibraries;
    protected List<Project> allLibraries;
    protected boolean reportIssues;
    protected Boolean gradleProject;
    protected Boolean supportLib;
    protected Boolean appCompat;
    protected GradleVersion gradleVersion;
    private Map<String, String> superClassMap;
    private ResourceVisibilityLookup resourceVisibility;
    private BuildToolInfo buildTools;
    private List<String> mCachedApplicableDensities;

    protected Project(LintClient client, File dir, File referenceDir) {
        this.manifestMinSdk = AndroidVersion.DEFAULT;
        this.manifestTargetSdk = AndroidVersion.DEFAULT;
        this.reportIssues = true;
        this.client = client;
        this.dir = dir;
        this.referenceDir = referenceDir;
        this.initialize();
    }

    public static Project create(LintClient client, File dir, File referenceDir) {
        return new Project(client, dir, referenceDir);
    }

    public static boolean isAospBuildEnvironment() {
        if (sAospBuild == null) {
            sAospBuild = Boolean.valueOf(getAospTop() != null);
        }

        return sAospBuild.booleanValue();
    }

    public static boolean isAospFrameworksRelatedProject(File dir) {
        if (isAospBuildEnvironment()) {
            File frameworks = new File(getAospTop(), "frameworks");
            String frameworksDir = frameworks.getAbsolutePath();
            String supportDir = (new File(frameworks, "support")).getAbsolutePath();
            if (dir.exists() && !dir.getAbsolutePath().startsWith(supportDir) && dir.getAbsolutePath().startsWith(frameworksDir) && !(new File(dir, "AndroidManifest.xml")).exists()) {
                return true;
            }
        }

        return false;
    }

    public static boolean isAospFrameworksProject(File dir) {
        String top = getAospTop();
        if (top != null) {
            File toCompare = new File(top, "frameworks" + File.separator + "base" + File.separator + "core");

            try {
                return dir.getCanonicalFile().equals(toCompare) && dir.exists();
            } catch (IOException var4) {
                return false;
            }
        } else {
            return false;
        }
    }

    private static String getAospTop() {
        return System.getenv("ANDROID_BUILD_TOP");
    }

    private static String getAospHostOut() {
        return System.getenv("ANDROID_HOST_OUT");
    }

    private static String getAospProductOut() {
        return System.getenv("ANDROID_PRODUCT_OUT");
    }

    private static AndroidVersion findCurrentAospVersion() {
        if (sCurrentVersion == null) {
            File versionMk = new File(getAospTop(), "build/core/version_defaults.mk".replace('/', File.separatorChar));
            if (!versionMk.exists()) {
                sCurrentVersion = AndroidVersion.DEFAULT;
                return sCurrentVersion;
            }

            int sdkVersion = 9;

            try {
                Pattern p = Pattern.compile("PLATFORM_SDK_VERSION\\s*:=\\s*(.*)");
                List<String> lines = Files.readLines(versionMk, Charsets.UTF_8);
                Iterator var4 = lines.iterator();

                while (var4.hasNext()) {
                    String line = (String) var4.next();
                    line = line.trim();
                    Matcher matcher = p.matcher(line);
                    if (matcher.matches()) {
                        String version = matcher.group(1);

                        try {
                            sdkVersion = Integer.parseInt(version);
                        } catch (NumberFormatException var9) {
                            ;
                        }
                        break;
                    }
                }
            } catch (IOException var10) {
                ;
            }

            sCurrentVersion = new AndroidVersion(sdkVersion, (String) null);
        }

        return sCurrentVersion;
    }

    private static void addResConfigsFromFlavor(Set<String> relevantDensities, List<String> variantFlavors, ProductFlavorContainer container) {
        ProductFlavor flavor = container.getProductFlavor();
        if ((variantFlavors == null || variantFlavors.contains(flavor.getName())) && !flavor.getResourceConfigurations().isEmpty()) {
            Iterator var4 = flavor.getResourceConfigurations().iterator();

            while (var4.hasNext()) {
                String densityName = (String) var4.next();
                Density density = Density.getEnum(densityName);
                if (density != null && density.isRecommended() && density != Density.NODPI && density != Density.ANYDPI) {
                    relevantDensities.add(densityName);
                }
            }
        }

    }

    public boolean isGradleProject() {
        if (this.gradleProject == null) {
            this.gradleProject = Boolean.valueOf(this.client.isGradleProject(this));
        }

        return this.gradleProject.booleanValue();
    }

    public boolean isAndroidProject() {
        return true;
    }

    public AndroidProject getGradleProjectModel() {
        return null;
    }

    public GradleVersion getGradleModelVersion() {
        if (this.gradleVersion == null && this.isGradleProject()) {
            AndroidProject gradleProjectModel = this.getGradleProjectModel();
            if (gradleProjectModel != null) {
                this.gradleVersion = GradleVersion.tryParse(gradleProjectModel.getModelVersion());
            }
        }

        return this.gradleVersion;
    }

    public AndroidLibrary getGradleLibraryModel() {
        return null;
    }

    public Variant getCurrentVariant() {
        return null;
    }

    protected void initialize() {
        try {
            Properties properties = new Properties();
            File propFile = new File(this.dir, "project.properties");
            if (propFile.exists()) {
                BufferedInputStream is = new BufferedInputStream(new FileInputStream(propFile));

                try {
                    properties.load(is);
                    String value = properties.getProperty("android.library");
                    this.library = "true".equals(value);
                    String proguardPath = properties.getProperty("proguard.config");
                    if (proguardPath != null) {
                        this.proguardPath = proguardPath;
                    }

                    this.mergeManifests = "true".equals(properties.getProperty("manifestmerger.enabled"));
                    String target = properties.getProperty("target");
                    int index;
                    String versionString;
                    if (target != null) {
                        index = target.lastIndexOf(45);
                        if (index == -1) {
                            index = target.lastIndexOf(58);
                        }

                        if (index != -1) {
                            versionString = target.substring(index + 1);

                            try {
                                this.buildSdk = Integer.parseInt(versionString);
                            } catch (NumberFormatException var23) {
                                this.client.log(Severity.WARNING, (Throwable) null, "Unexpected build target format: %1$s", new Object[]{target});
                            }
                        }
                    }

                    for (index = 1; index < 1000; ++index) {
                        versionString = String.format("android.library.reference.%1$d", new Object[]{Integer.valueOf(index)});
                        String library = properties.getProperty(versionString);
                        if (library == null || library.isEmpty()) {
                            break;
                        }

                        File libraryDir = (new File(this.dir, library)).getCanonicalFile();
                        if (this.directLibraries == null) {
                            this.directLibraries = new ArrayList();
                        }

                        File libraryReferenceDir = this.referenceDir;
                        if (!libraryDir.getPath().startsWith(this.referenceDir.getPath())) {
                            libraryReferenceDir = libraryReferenceDir.getCanonicalFile();
                            if (!libraryDir.getPath().startsWith(this.referenceDir.getPath())) {
                                for (File file = libraryReferenceDir; file != null && !file.getPath().isEmpty(); file = file.getParentFile()) {
                                    if (libraryDir.getPath().startsWith(file.getPath())) {
                                        libraryReferenceDir = file;
                                        break;
                                    }
                                }
                            }
                        }

                        try {
                            Project libraryPrj = this.client.getProject(libraryDir, libraryReferenceDir);
                            this.directLibraries.add(libraryPrj);
                            libraryPrj.setReportIssues(false);
                        } catch (CircularDependencyException var22) {
                            var22.setProject(this);
                            var22.setLocation(Location.create(propFile));
                            throw var22;
                        }
                    }
                } finally {
                    try {
                        Closeables.close(is, true);
                    } catch (IOException var21) {
                        ;
                    }

                }
            }
        } catch (IOException var25) {
            this.client.log(var25, "Initializing project state", new Object[0]);
        }

        if (this.directLibraries != null) {
            this.directLibraries = Collections.unmodifiableList(this.directLibraries);
        } else {
            this.directLibraries = Collections.emptyList();
        }

        if (isAospBuildEnvironment()) {
            if (isAospFrameworksRelatedProject(this.dir)) {
                this.manifestMinSdk = this.manifestTargetSdk = new AndroidVersion(25, (String) null);
            } else if (this.buildSdk == -1) {
                this.buildSdk = this.getClient().getHighestKnownApiLevel();
            }
        }

    }

    public String toString() {
        return "Project [dir=" + this.dir + ']';
    }

    public int hashCode() {
        return this.dir.hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            Project other = (Project) obj;
            return this.dir.equals(other.dir);
        }
    }

    public void addFile(File file) {
        if (this.files == null) {
            this.files = new ArrayList();
        }

        this.files.add(file);
    }

    public List<File> getSubset() {
        return this.files;
    }

    public List<File> getJavaSourceFolders() {
        if (this.javaSourceFolders == null) {
            if (isAospFrameworksRelatedProject(this.dir)) {
                return Collections.singletonList(new File(this.dir, "java"));
            }

            if (isAospBuildEnvironment()) {
                String top = getAospTop();
                if (this.dir.getAbsolutePath().startsWith(top)) {
                    this.javaSourceFolders = this.getAospJavaSourcePath();
                    return this.javaSourceFolders;
                }
            }

            this.javaSourceFolders = this.client.getJavaSourceFolders(this);
        }

        return this.javaSourceFolders;
    }

    public List<File> getJavaClassFolders() {
        if (this.javaClassFolders == null) {
            String top;
            if (isAospFrameworksProject(this.dir)) {
                top = getAospTop();
                if (top != null) {
                    File out = new File(top, "out");
                    if (out.exists()) {
                        String relative = "target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar";
                        File jar = new File(out, relative.replace('/', File.separatorChar));
                        if (jar.exists()) {
                            this.javaClassFolders = Collections.singletonList(jar);
                            return this.javaClassFolders;
                        }
                    }
                }
            }

            if (isAospBuildEnvironment()) {
                top = getAospTop();
                if (this.dir.getAbsolutePath().startsWith(top)) {
                    this.javaClassFolders = this.getAospJavaClassPath();
                    return this.javaClassFolders;
                }
            }

            this.javaClassFolders = this.client.getJavaClassFolders(this);
        }

        return this.javaClassFolders;
    }

    public List<File> getJavaLibraries(boolean includeProvided) {
        if (includeProvided) {
            if (this.javaLibraries == null) {
                this.javaLibraries = this.client.getJavaLibraries(this, true);
                if (isAospBuildEnvironment()) {
                    File out = new File(getAospTop(), "out");
                    String relative = "target/common/obj/JAVA_LIBRARIES/android-support-annotations_intermediates/classes";
                    File annotationsDir = new File(out, relative.replace('/', File.separatorChar));
                    if (annotationsDir.exists()) {
                        this.javaLibraries.add(annotationsDir);
                    }
                }
            }

            return this.javaLibraries;
        } else {
            if (this.nonProvidedJavaLibraries == null) {
                this.nonProvidedJavaLibraries = this.client.getJavaLibraries(this, false);
            }

            return this.nonProvidedJavaLibraries;
        }
    }

    public List<File> getTestSourceFolders() {
        if (this.testSourceFolders == null) {
            this.testSourceFolders = this.client.getTestSourceFolders(this);
        }

        return this.testSourceFolders;
    }

    public List<File> getTestLibraries() {
        if (this.testLibraries == null) {
            this.testLibraries = this.client.getTestLibraries(this);
        }

        return this.testLibraries;
    }

    public List<File> getResourceFolders() {
        if (this.resourceFolders == null) {
            List<File> folders = this.client.getResourceFolders(this);
            if (folders.size() == 1 && isAospFrameworksRelatedProject(this.dir)) {
                this.manifestMinSdk = this.manifestTargetSdk = new AndroidVersion(25, (String) null);
                File folder = new File((File) folders.get(0), "res");
                if (!folder.exists()) {
                    folders = Collections.emptyList();
                }
            }

            this.resourceFolders = folders;
        }

        return this.resourceFolders;
    }

    public List<File> getAssetFolders() {
        if (this.assetFolders == null) {
            this.assetFolders = this.client.getAssetFolders(this);
        }

        return this.assetFolders;
    }

    public String getDisplayPath(File file) {
        String path = file.getPath();
        String referencePath = this.referenceDir.getPath();
        if (path.startsWith(referencePath)) {
            int length = referencePath.length();
            if (path.length() > length && path.charAt(length) == File.separatorChar) {
                ++length;
            }

            return path.substring(length);
        } else {
            return path;
        }
    }

    public String getRelativePath(File file) {
        String path = file.getPath();
        String referencePath = this.dir.getPath();
        if (path.startsWith(referencePath)) {
            int length = referencePath.length();
            if (path.length() > length && path.charAt(length) == File.separatorChar) {
                ++length;
            }

            return path.substring(length);
        } else {
            return path;
        }
    }

    public File getDir() {
        return this.dir;
    }

    public File getReferenceDir() {
        return this.referenceDir;
    }

    public Configuration getConfiguration(LintDriver driver) {
        if (this.configuration == null) {
            this.configuration = this.client.getConfiguration(this, driver);
        }

        return this.configuration;
    }

    public String getPackage() {
        return this.pkg;
    }

    public AndroidVersion getMinSdkVersion() {
        return this.manifestMinSdk == null ? AndroidVersion.DEFAULT : this.manifestMinSdk;
    }

    public int getMinSdk() {
        AndroidVersion version = this.getMinSdkVersion();
        return version == AndroidVersion.DEFAULT ? -1 : version.getApiLevel();
    }

    public AndroidVersion getTargetSdkVersion() {
        return this.manifestTargetSdk == AndroidVersion.DEFAULT ? this.getMinSdkVersion() : this.manifestTargetSdk;
    }

    public int getTargetSdk() {
        AndroidVersion version = this.getTargetSdkVersion();
        return version == AndroidVersion.DEFAULT ? -1 : version.getApiLevel();
    }

    public int getBuildSdk() {
        return this.buildSdk;
    }

    public BuildToolInfo getBuildTools() {
        if (this.buildTools == null) {
            this.buildTools = this.client.getBuildTools(this);
        }

        return this.buildTools;
    }

    public IAndroidTarget getBuildTarget() {
        if (this.target == null) {
            this.target = this.client.getCompileTarget(this);
        }

        return this.target;
    }

    public void readManifest(Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            this.pkg = root.getAttribute("package");
            this.manifestMinSdk = AndroidVersion.DEFAULT;
            this.manifestTargetSdk = AndroidVersion.DEFAULT;
            NodeList usesSdks = root.getElementsByTagName("uses-sdk");
            if (usesSdks.getLength() > 0) {
                Element element = (Element) usesSdks.item(0);
                String minSdk = null;
                if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "minSdkVersion")) {
                    minSdk = element.getAttributeNS("http://schemas.android.com/apk/res/android", "minSdkVersion");
                }

                if (minSdk != null) {
                    IAndroidTarget[] targets = this.client.getTargets();
                    this.manifestMinSdk = SdkVersionInfo.getVersion(minSdk, targets);
                }

                if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "targetSdkVersion")) {
                    String targetSdk = element.getAttributeNS("http://schemas.android.com/apk/res/android", "targetSdkVersion");
                    if (targetSdk != null) {
                        IAndroidTarget[] targets = this.client.getTargets();
                        this.manifestTargetSdk = SdkVersionInfo.getVersion(targetSdk, targets);
                    }
                } else {
                    this.manifestTargetSdk = this.manifestMinSdk;
                }
            } else if (isAospBuildEnvironment()) {
                this.extractAospMinSdkVersion();
                this.manifestTargetSdk = this.manifestMinSdk;
            }

        }
    }

    public boolean isLibrary() {
        return this.library;
    }

    public List<Project> getDirectLibraries() {
        return this.directLibraries != null ? this.directLibraries : new ArrayList<Project>();
    }

    public List<Project> getAllLibraries() {
        if (this.allLibraries == null) {
            if (this.directLibraries.isEmpty()) {
                return this.directLibraries;
            }

            List<Project> all = new ArrayList();
            Set<Project> seen = Sets.newHashSet();
            Set<Project> path = Sets.newHashSet();
            seen.add(this);
            path.add(this);
            this.addLibraryProjects(all, seen, path);
            this.allLibraries = all;
        }

        return this.allLibraries;
    }

    private void addLibraryProjects(Collection<Project> collection, Set<Project> seen, Set<Project> path) {
        Iterator var4 = this.directLibraries.iterator();

        while (var4.hasNext()) {
            Project library = (Project) var4.next();
            if (seen.contains(library)) {
                if (path.contains(library)) {
                    this.client.log(Severity.WARNING, (Throwable) null, "Internal lint error: cyclic library dependency for %1$s", new Object[]{library});
                }
            } else {
                collection.add(library);
                seen.add(library);
                path.add(library);
                library.addLibraryProjects(collection, seen, path);
                path.remove(library);
            }
        }

    }

    public SdkInfo getSdkInfo() {
        if (this.sdkInfo == null) {
            this.sdkInfo = this.client.getSdkInfo(this);
        }

        return this.sdkInfo;
    }

    public List<File> getManifestFiles() {
        if (this.manifestFiles == null) {
            File manifestFile = new File(this.dir, "AndroidManifest.xml");
            if (manifestFile.exists()) {
                this.manifestFiles = Collections.singletonList(manifestFile);
            } else {
                this.manifestFiles = Collections.emptyList();
            }
        }

        return this.manifestFiles;
    }

    public List<File> getProguardFiles() {
        if (this.proguardFiles == null) {
            List<File> files = new ArrayList();
            if (this.proguardPath != null) {
                Splitter splitter = Splitter.on(CharMatcher.anyOf(":;"));
                Iterator var3 = splitter.split(this.proguardPath).iterator();

                while (var3.hasNext()) {
                    String path = (String) var3.next();
                    if (!path.contains("${")) {
                        File file = new File(path);
                        if (!file.isAbsolute()) {
                            file = new File(this.getDir(), path);
                        }

                        if (file.exists()) {
                            files.add(file);
                        }
                    }
                }
            }

            if (files.isEmpty()) {
                File file = new File(this.getDir(), "proguard.cfg");
                if (file.exists()) {
                    files.add(file);
                }

                file = new File(this.getDir(), "proguard-project.txt");
                if (file.exists()) {
                    files.add(file);
                }
            }

            this.proguardFiles = files;
        }

        return this.proguardFiles;
    }

    public List<File> getGradleBuildScripts() {
        if (this.gradleFiles == null) {
            if (this.isGradleProject()) {
                this.gradleFiles = Lists.newArrayListWithExpectedSize(2);
                File build = new File(this.dir, "build.gradle");
                if (build.exists()) {
                    this.gradleFiles.add(build);
                }

                File settings = new File(this.dir, "settings.gradle");
                if (settings.exists()) {
                    this.gradleFiles.add(settings);
                }
            } else {
                this.gradleFiles = Collections.emptyList();
            }
        }

        return this.gradleFiles;
    }

    public String getName() {
        if (this.name == null) {
            this.name = this.client.getProjectName(this);
        }

        return this.name;
    }

    public void setName(String name) {
        assert !name.isEmpty();

        this.name = name;
    }

    public boolean getReportIssues() {
        return this.reportIssues;
    }

    public void setReportIssues(boolean reportIssues) {
        this.reportIssues = reportIssues;
    }

    public boolean isMergingManifests() {
        return this.mergeManifests;
    }

    private List<File> getAospJavaSourcePath() {
        List<File> sources = new ArrayList(2);
        File src = new File(this.dir, "src");
        if (src.exists()) {
            sources.add(src);
        }

        Iterator var3 = this.getIntermediateDirs().iterator();

        while (var3.hasNext()) {
            File dir = (File) var3.next();
            File classes = new File(dir, "src");
            if (classes.exists()) {
                sources.add(classes);
            }
        }

        if (sources.isEmpty()) {
            this.client.log((Throwable) null, "Warning: Could not find sources or generated sources for project %1$s", new Object[]{this.getName()});
        }

        return sources;
    }

    private List<File> getAospJavaClassPath() {
        List<File> classDirs = new ArrayList(1);
        Iterator var2 = this.getIntermediateDirs().iterator();

        while (var2.hasNext()) {
            File dir = (File) var2.next();
            File classes = new File(dir, "classes");
            if (classes.exists()) {
                classDirs.add(classes);
            } else {
                classes = new File(dir, "classes.jar");
                if (classes.exists()) {
                    classDirs.add(classes);
                }
            }
        }

        if (classDirs.isEmpty()) {
            this.client.log((Throwable) null, "No bytecode found: Has the project been built? (%1$s)", new Object[]{this.getName()});
        }

        return classDirs;
    }

    private List<File> getIntermediateDirs() {
        List<File> intermediates = new ArrayList();
        String moduleName = this.dir.getName();

        try {
            moduleName = this.dir.getCanonicalFile().getName();
        } catch (IOException var16) {
            ;
        }
        moduleName = getModuleName(dir);
        log("ModuleName: " + moduleName);

        String top = getAospTop();
        String[] outFolders = new String[]{top + "/out/host/common/obj", top + "/out/target/common/obj", getAospHostOut() + "/obj", getAospProductOut() + "/obj"};
        String[] moduleClasses = new String[]{"APPS", "JAVA_LIBRARIES"};
        String[] var6 = outFolders;
        int var7 = outFolders.length;

        for (int var8 = 0; var8 < var7; ++var8) {
            String out = var6[var8];

            assert (new File(out.replace('/', File.separatorChar))).exists() : out;

            String[] var10 = moduleClasses;
            int var11 = moduleClasses.length;

            for (int var12 = 0; var12 < var11; ++var12) {
                String moduleClass = var10[var12];
                String path = out + '/' + moduleClass + '/' + moduleName + "_intermediates";
                File file = new File(path.replace('/', File.separatorChar));
                //log("getIntermediateDirs: " + path);
                if (file.exists()) {
                    intermediates.add(file);
                }
            }
        }

        return intermediates;
    }

    private String getModuleName(File dir) {
        String moduleName = dir.getName();
        File mk = new File(dir, "Android.mk");
        Pattern pattern = Pattern.compile("LOCAL_PACKAGE_NAME\\W*:=\\W*(.*)");
        if (mk.exists()) {
            try {
                List<String> lines = Files.readLines(mk, Charset.defaultCharset());
                for (String line : lines) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        moduleName = matcher.group(1);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return moduleName;
    }

    private void extractAospMinSdkVersion() {
        boolean found = false;
        File makefile = new File(this.dir, "Android.mk");
        if (makefile.exists()) {
            try {
                List<String> lines = Files.readLines(makefile, Charsets.UTF_8);
                Pattern p = Pattern.compile("LOCAL_SDK_VERSION\\s*:=\\s*(.*)");
                Iterator var5 = lines.iterator();

                while (var5.hasNext()) {
                    String line = (String) var5.next();
                    line = line.trim();
                    Matcher matcher = p.matcher(line);
                    if (matcher.matches()) {
                        found = true;
                        String version = matcher.group(1);
                        if (version.equals("current")) {
                            this.manifestMinSdk = findCurrentAospVersion();
                        } else {
                            this.manifestMinSdk = SdkVersionInfo.getVersion(version, this.client.getTargets());
                        }
                        break;
                    }
                }
            } catch (IOException var9) {
                this.client.log(var9, (String) null, new Object[0]);
            }
        }

        if (!found) {
            this.manifestMinSdk = findCurrentAospVersion();
        }

    }

    public Boolean dependsOn(String artifact) {
        Iterator var2;
        File file;
        String name;
        Project dependency;
        Boolean b;
        if ("com.android.support:support-v4".equals(artifact)) {
            if (this.supportLib == null) {
                var2 = this.getJavaLibraries(true).iterator();

                label61:
                {
                    do {
                        if (!var2.hasNext()) {
                            break label61;
                        }

                        file = (File) var2.next();
                        name = file.getName();
                    } while (!name.equals("android-support-v4.jar") && !name.startsWith("support-v4-"));

                    this.supportLib = Boolean.valueOf(true);
                }

                if (this.supportLib == null) {
                    var2 = this.getDirectLibraries().iterator();

                    while (var2.hasNext()) {
                        dependency = (Project) var2.next();
                        b = dependency.dependsOn(artifact);
                        if (b != null && b.booleanValue()) {
                            this.supportLib = Boolean.valueOf(true);
                            break;
                        }
                    }
                }

                if (this.supportLib == null) {
                    this.supportLib = Boolean.valueOf(false);
                }
            }

            return this.supportLib;
        } else if (!"com.android.support:appcompat-v7".equals(artifact)) {
            return null;
        } else {
            if (this.appCompat == null) {
                var2 = this.getJavaLibraries(true).iterator();

                while (var2.hasNext()) {
                    file = (File) var2.next();
                    name = file.getName();
                    if (name.startsWith("appcompat-v7-")) {
                        this.appCompat = Boolean.valueOf(true);
                        break;
                    }
                }

                if (this.appCompat == null) {
                    var2 = this.getDirectLibraries().iterator();

                    while (var2.hasNext()) {
                        dependency = (Project) var2.next();
                        b = dependency.dependsOn(artifact);
                        if (b != null && b.booleanValue()) {
                            this.appCompat = Boolean.valueOf(true);
                            break;
                        }
                    }
                }

                if (this.appCompat == null) {
                    this.appCompat = Boolean.valueOf(false);
                }
            }

            return this.appCompat;
        }
    }

    public List<String> getApplicableDensities() {
        if (this.mCachedApplicableDensities == null) {
            if (this.isGradleProject() && this.getGradleProjectModel() != null && this.getCurrentVariant() != null) {
                Set<String> relevantDensities = Sets.newHashSet();
                Variant variant = this.getCurrentVariant();
                List<String> variantFlavors = variant.getProductFlavors();
                AndroidProject gradleProjectModel = this.getGradleProjectModel();
                addResConfigsFromFlavor(relevantDensities, (List) null, this.getGradleProjectModel().getDefaultConfig());
                Iterator var5 = gradleProjectModel.getProductFlavors().iterator();

                while (var5.hasNext()) {
                    ProductFlavorContainer container = (ProductFlavorContainer) var5.next();
                    addResConfigsFromFlavor(relevantDensities, variantFlavors, container);
                }

                if (relevantDensities.isEmpty()) {
                    AndroidArtifact mainArtifact = variant.getMainArtifact();
                    Collection<AndroidArtifactOutput> outputs = mainArtifact.getOutputs();
                    Iterator var7 = outputs.iterator();

                    label65:
                    while (var7.hasNext()) {
                        AndroidArtifactOutput output = (AndroidArtifactOutput) var7.next();
                        Iterator var9 = output.getOutputs().iterator();

                        while (true) {
                            OutputFile file;
                            String DENSITY_NAME;
                            do {
                                if (!var9.hasNext()) {
                                    continue label65;
                                }

                                file = (OutputFile) var9.next();
                                DENSITY_NAME = FilterType.DENSITY.name();
                            } while (!file.getFilterTypes().contains(DENSITY_NAME));

                            Iterator var12 = file.getFilters().iterator();

                            while (var12.hasNext()) {
                                FilterData data = (FilterData) var12.next();
                                if (DENSITY_NAME.equals(data.getFilterType())) {
                                    relevantDensities.add(data.getIdentifier());
                                }
                            }
                        }
                    }
                }

                if (!relevantDensities.isEmpty()) {
                    this.mCachedApplicableDensities = Lists.newArrayListWithExpectedSize(10);
                    var5 = relevantDensities.iterator();

                    while (var5.hasNext()) {
                        String density = (String) var5.next();
                        String folder = ResourceFolderType.DRAWABLE.getName() + '-' + density;
                        this.mCachedApplicableDensities.add(folder);
                    }

                    Collections.sort(this.mCachedApplicableDensities);
                } else {
                    this.mCachedApplicableDensities = Collections.emptyList();
                }
            } else {
                this.mCachedApplicableDensities = Collections.emptyList();
            }
        }

        return this.mCachedApplicableDensities.isEmpty() ? null : this.mCachedApplicableDensities;
    }

    public Map<String, String> getSuperClassMap() {
        if (this.superClassMap == null) {
            this.superClassMap = this.client.createSuperClassMap(this);
        }

        return this.superClassMap;
    }

    public ResourceVisibilityLookup getResourceVisibility() {
        if (this.resourceVisibility == null) {
            if (this.isGradleProject()) {
                AndroidProject project = this.getGradleProjectModel();
                Variant variant = this.getCurrentVariant();
                if (project != null && variant != null) {
                    this.resourceVisibility = this.client.getResourceVisibilityProvider().get(project, variant);
                } else if (this.getGradleLibraryModel() != null) {
                    try {
                        this.resourceVisibility = this.client.getResourceVisibilityProvider().get(this.getGradleLibraryModel());
                    } catch (Exception var4) {
                        ;
                    }
                }
            }

            if (this.resourceVisibility == null) {
                this.resourceVisibility = ResourceVisibilityLookup.NONE;
            }
        }

        return this.resourceVisibility;
    }

    public LintClient getClient() {
        return this.client;
    }

    private void log(String str) {
        this.client.log(Severity.WARNING, null, str, null);
    }

}
