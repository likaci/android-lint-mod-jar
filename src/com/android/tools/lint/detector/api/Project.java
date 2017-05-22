//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.client.api.CircularDependencyException;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.LintClient;
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
    protected final LintClient mClient;
    protected final File mDir;
    protected final File mReferenceDir;
    protected Configuration mConfiguration;
    protected String mPackage;
    protected int mBuildSdk = -1;
    protected IAndroidTarget mTarget;
    protected AndroidVersion mManifestMinSdk;
    protected AndroidVersion mManifestTargetSdk;
    protected boolean mLibrary;
    protected String mName;
    protected String mProguardPath;
    protected boolean mMergeManifests;
    protected SdkInfo mSdkInfo;
    protected List<File> mFiles;
    protected List<File> mProguardFiles;
    protected List<File> mGradleFiles;
    protected List<File> mManifestFiles;
    protected List<File> mJavaSourceFolders;
    protected List<File> mJavaClassFolders;
    protected List<File> mJavaLibraries;
    protected List<File> mTestSourceFolders;
    protected List<File> mResourceFolders;
    protected List<Project> mDirectLibraries;
    protected List<Project> mAllLibraries;
    protected boolean mReportIssues;
    protected Boolean mGradleProject;
    protected Boolean mSupportLib;
    protected Boolean mAppCompat;
    private Map<String, String> mSuperClassMap;
    private ResourceVisibilityLookup mResourceVisibility;
    private static Boolean sAospBuild;
    private static AndroidVersion sCurrentVersion;
    private List<String> mCachedApplicableDensities;

    @NonNull
    public static Project create(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir) {
        return new Project(client, dir, referenceDir);
    }

    public boolean isGradleProject() {
        if(this.mGradleProject == null) {
            this.mGradleProject = Boolean.valueOf(this.mClient.isGradleProject(this));
        }

        return this.mGradleProject.booleanValue();
    }

    public boolean isAndroidProject() {
        return true;
    }

    @Nullable
    public AndroidProject getGradleProjectModel() {
        return null;
    }

    @Nullable
    public AndroidLibrary getGradleLibraryModel() {
        return null;
    }

    @Nullable
    public Variant getCurrentVariant() {
        return null;
    }

    protected Project(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir) {
        this.mManifestMinSdk = AndroidVersion.DEFAULT;
        this.mManifestTargetSdk = AndroidVersion.DEFAULT;
        this.mReportIssues = true;
        this.mClient = client;
        this.mDir = dir;
        this.mReferenceDir = referenceDir;
        this.initialize();
    }

    protected void initialize() {
        try {
            Properties properties = new Properties();
            File propFile = new File(this.mDir, "project.properties");
            if(propFile.exists()) {
                BufferedInputStream is = new BufferedInputStream(new FileInputStream(propFile));

                try {
                    properties.load(is);
                    String value = properties.getProperty("android.library");
                    this.mLibrary = "true".equals(value);
                    String proguardPath = properties.getProperty("proguard.config");
                    if(proguardPath != null) {
                        this.mProguardPath = proguardPath;
                    }

                    this.mMergeManifests = "true".equals(properties.getProperty("manifestmerger.enabled"));
                    String target = properties.getProperty("target");
                    int index;
                    String versionString;
                    if(target != null) {
                        index = target.lastIndexOf(45);
                        if(index == -1) {
                            index = target.lastIndexOf(58);
                        }

                        if(index != -1) {
                            versionString = target.substring(index + 1);

                            try {
                                this.mBuildSdk = Integer.parseInt(versionString);
                            } catch (NumberFormatException var23) {
                                this.mClient.log(Severity.WARNING, (Throwable)null, "Unexpected build target format: %1$s", new Object[]{target});
                            }
                        }
                    }

                    for(index = 1; index < 1000; ++index) {
                        versionString = String.format("android.library.reference.%1$d", new Object[]{Integer.valueOf(index)});
                        String library = properties.getProperty(versionString);
                        if(library == null || library.isEmpty()) {
                            break;
                        }

                        File libraryDir = (new File(this.mDir, library)).getCanonicalFile();
                        if(this.mDirectLibraries == null) {
                            this.mDirectLibraries = new ArrayList();
                        }

                        File libraryReferenceDir = this.mReferenceDir;
                        if(!libraryDir.getPath().startsWith(this.mReferenceDir.getPath())) {
                            libraryReferenceDir = libraryReferenceDir.getCanonicalFile();
                            if(!libraryDir.getPath().startsWith(this.mReferenceDir.getPath())) {
                                for(File file = libraryReferenceDir; file != null && !file.getPath().isEmpty(); file = file.getParentFile()) {
                                    if(libraryDir.getPath().startsWith(file.getPath())) {
                                        libraryReferenceDir = file;
                                        break;
                                    }
                                }
                            }
                        }

                        try {
                            Project libraryPrj = this.mClient.getProject(libraryDir, libraryReferenceDir);
                            this.mDirectLibraries.add(libraryPrj);
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
            this.mClient.log(var25, "Initializing project state", new Object[0]);
        }

        if(this.mDirectLibraries != null) {
            this.mDirectLibraries = Collections.unmodifiableList(this.mDirectLibraries);
        } else {
            this.mDirectLibraries = Collections.emptyList();
        }

    }

    public String toString() {
        return "Project [dir=" + this.mDir + ']';
    }

    public int hashCode() {
        return this.mDir.hashCode();
    }

    public boolean equals(@Nullable Object obj) {
        if(this == obj) {
            return true;
        } else if(obj == null) {
            return false;
        } else if(this.getClass() != obj.getClass()) {
            return false;
        } else {
            Project other = (Project)obj;
            return this.mDir.equals(other.mDir);
        }
    }

    public void addFile(@NonNull File file) {
        if(this.mFiles == null) {
            this.mFiles = new ArrayList();
        }

        this.mFiles.add(file);
    }

    @Nullable
    public List<File> getSubset() {
        return this.mFiles;
    }

    @NonNull
    public List<File> getJavaSourceFolders() {
        if(this.mJavaSourceFolders == null) {
            if(isAospFrameworksProject(this.mDir)) {
                return Collections.singletonList(new File(this.mDir, "java"));
            }

            if(isAospBuildEnvironment()) {
                String top = getAospTop();
                if(this.mDir.getAbsolutePath().startsWith(top)) {
                    this.mJavaSourceFolders = this.getAospJavaSourcePath();
                    return this.mJavaSourceFolders;
                }
            }

            this.mJavaSourceFolders = this.mClient.getJavaSourceFolders(this);
        }

        return this.mJavaSourceFolders;
    }

    @NonNull
    public List<File> getJavaClassFolders() {
        if(this.mJavaClassFolders == null) {
            if(isAospFrameworksProject(this.mDir)) {
                File top = this.mDir.getParentFile().getParentFile().getParentFile();
                if(top != null) {
                    File out = new File(top, "out");
                    if(out.exists()) {
                        String relative = "target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar";
                        File jar = new File(out, relative.replace('/', File.separatorChar));
                        if(jar.exists()) {
                            this.mJavaClassFolders = Collections.singletonList(jar);
                            return this.mJavaClassFolders;
                        }
                    }
                }
            }

            if(isAospBuildEnvironment()) {
                String top = getAospTop();
                if(this.mDir.getAbsolutePath().startsWith(top)) {
                    this.mJavaClassFolders = this.getAospJavaClassPath();
                    return this.mJavaClassFolders;
                }
            }

            this.mJavaClassFolders = this.mClient.getJavaClassFolders(this);
        }

        return this.mJavaClassFolders;
    }

    @NonNull
    public List<File> getJavaLibraries() {
        if(this.mJavaLibraries == null) {
            this.mJavaLibraries = this.mClient.getJavaLibraries(this);
        }

        return this.mJavaLibraries;
    }

    @NonNull
    public List<File> getTestSourceFolders() {
        if(this.mTestSourceFolders == null) {
            this.mTestSourceFolders = this.mClient.getTestSourceFolders(this);
        }

        return this.mTestSourceFolders;
    }

    @NonNull
    public List<File> getResourceFolders() {
        if(this.mResourceFolders == null) {
            List<File> folders = this.mClient.getResourceFolders(this);
            if(folders.size() == 1 && isAospFrameworksProject(this.mDir)) {
                this.mManifestMinSdk = this.mManifestTargetSdk = new AndroidVersion(22, (String)null);
                File folder = new File((File)folders.get(0), "res");
                if(!folder.exists()) {
                    folders = Collections.emptyList();
                }
            }

            this.mResourceFolders = folders;
        }

        return this.mResourceFolders;
    }

    @NonNull
    public String getDisplayPath(@NonNull File file) {
        String path = file.getPath();
        String referencePath = this.mReferenceDir.getPath();
        if(path.startsWith(referencePath)) {
            int length = referencePath.length();
            if(path.length() > length && path.charAt(length) == File.separatorChar) {
                ++length;
            }

            return path.substring(length);
        } else {
            return path;
        }
    }

    @NonNull
    public String getRelativePath(@NonNull File file) {
        String path = file.getPath();
        String referencePath = this.mDir.getPath();
        if(path.startsWith(referencePath)) {
            int length = referencePath.length();
            if(path.length() > length && path.charAt(length) == File.separatorChar) {
                ++length;
            }

            return path.substring(length);
        } else {
            return path;
        }
    }

    @NonNull
    public File getDir() {
        return this.mDir;
    }

    @NonNull
    public File getReferenceDir() {
        return this.mReferenceDir;
    }

    @NonNull
    public Configuration getConfiguration() {
        if(this.mConfiguration == null) {
            this.mConfiguration = this.mClient.getConfiguration(this);
        }

        return this.mConfiguration;
    }

    @Nullable
    public String getPackage() {
        return this.mPackage;
    }

    @NonNull
    public AndroidVersion getMinSdkVersion() {
        return this.mManifestMinSdk == null?AndroidVersion.DEFAULT:this.mManifestMinSdk;
    }

    public int getMinSdk() {
        AndroidVersion version = this.getMinSdkVersion();
        return version == AndroidVersion.DEFAULT?-1:version.getApiLevel();
    }

    @NonNull
    public AndroidVersion getTargetSdkVersion() {
        return this.mManifestTargetSdk == AndroidVersion.DEFAULT?this.getMinSdkVersion():this.mManifestTargetSdk;
    }

    public int getTargetSdk() {
        AndroidVersion version = this.getTargetSdkVersion();
        return version == AndroidVersion.DEFAULT?-1:version.getApiLevel();
    }

    public int getBuildSdk() {
        return this.mBuildSdk;
    }

    @Nullable
    public IAndroidTarget getBuildTarget() {
        if(this.mTarget == null) {
            this.mTarget = this.mClient.getCompileTarget(this);
        }

        return this.mTarget;
    }

    public void readManifest(@NonNull Document document) {
        Element root = document.getDocumentElement();
        if(root != null) {
            this.mPackage = root.getAttribute("package");
            if(this.mPackage != null && this.mPackage.startsWith("android.support.")) {
                this.mReportIssues = false;
            }

            this.mManifestMinSdk = AndroidVersion.DEFAULT;
            this.mManifestTargetSdk = AndroidVersion.DEFAULT;
            NodeList usesSdks = root.getElementsByTagName("uses-sdk");
            if(usesSdks.getLength() > 0) {
                Element element = (Element)usesSdks.item(0);
                String minSdk = null;
                if(element.hasAttributeNS("http://schemas.android.com/apk/res/android", "minSdkVersion")) {
                    minSdk = element.getAttributeNS("http://schemas.android.com/apk/res/android", "minSdkVersion");
                }

                if(minSdk != null) {
                    IAndroidTarget[] targets = this.mClient.getTargets();
                    this.mManifestMinSdk = SdkVersionInfo.getVersion(minSdk, targets);
                }

                if(element.hasAttributeNS("http://schemas.android.com/apk/res/android", "targetSdkVersion")) {
                    String targetSdk = element.getAttributeNS("http://schemas.android.com/apk/res/android", "targetSdkVersion");
                    if(targetSdk != null) {
                        IAndroidTarget[] targets = this.mClient.getTargets();
                        this.mManifestTargetSdk = SdkVersionInfo.getVersion(targetSdk, targets);
                    }
                } else {
                    this.mManifestTargetSdk = this.mManifestMinSdk;
                }
            } else if(isAospBuildEnvironment()) {
                this.extractAospMinSdkVersion();
                this.mManifestTargetSdk = this.mManifestMinSdk;
            }

        }
    }

    public boolean isLibrary() {
        return this.mLibrary;
    }

    @NonNull
    public List<Project> getDirectLibraries() {
        return mDirectLibraries != null ? mDirectLibraries : Collections.<Project>emptyList();
    }

    @NonNull
    public List<Project> getAllLibraries() {
        if(this.mAllLibraries == null) {
            if(this.mDirectLibraries.isEmpty()) {
                return this.mDirectLibraries;
            }

            List<Project> all = new ArrayList();
            Set<Project> seen = Sets.newHashSet();
            Set<Project> path = Sets.newHashSet();
            seen.add(this);
            path.add(this);
            this.addLibraryProjects(all, seen, path);
            this.mAllLibraries = all;
        }

        return this.mAllLibraries;
    }

    private void addLibraryProjects(@NonNull Collection<Project> collection, @NonNull Set<Project> seen, @NonNull Set<Project> path) {
        Iterator i$ = this.mDirectLibraries.iterator();

        while(i$.hasNext()) {
            Project library = (Project)i$.next();
            if(seen.contains(library)) {
                if(path.contains(library)) {
                    this.mClient.log(Severity.WARNING, (Throwable)null, "Internal lint error: cyclic library dependency for %1$s", new Object[]{library});
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

    @NonNull
    public SdkInfo getSdkInfo() {
        if(this.mSdkInfo == null) {
            this.mSdkInfo = this.mClient.getSdkInfo(this);
        }

        return this.mSdkInfo;
    }

    @NonNull
    public List<File> getManifestFiles() {
        if(this.mManifestFiles == null) {
            File manifestFile = new File(this.mDir, "AndroidManifest.xml");
            if(manifestFile.exists()) {
                this.mManifestFiles = Collections.singletonList(manifestFile);
            } else {
                this.mManifestFiles = Collections.emptyList();
            }
        }

        return this.mManifestFiles;
    }

    @NonNull
    public List<File> getProguardFiles() {
        if(this.mProguardFiles == null) {
            List<File> files = new ArrayList();
            if(this.mProguardPath != null) {
                Splitter splitter = Splitter.on(CharMatcher.anyOf(":;"));
                Iterator i$ = splitter.split(this.mProguardPath).iterator();

                while(i$.hasNext()) {
                    String path = (String)i$.next();
                    if(!path.contains("${")) {
                        File file = new File(path);
                        if(!file.isAbsolute()) {
                            file = new File(this.getDir(), path);
                        }

                        if(file.exists()) {
                            files.add(file);
                        }
                    }
                }
            }

            if(files.isEmpty()) {
                File file = new File(this.getDir(), "proguard.cfg");
                if(file.exists()) {
                    files.add(file);
                }

                file = new File(this.getDir(), "proguard-project.txt");
                if(file.exists()) {
                    files.add(file);
                }
            }

            this.mProguardFiles = files;
        }

        return this.mProguardFiles;
    }

    @NonNull
    public List<File> getGradleBuildScripts() {
        if(this.mGradleFiles == null) {
            if(this.isGradleProject()) {
                this.mGradleFiles = Lists.newArrayListWithExpectedSize(2);
                File build = new File(this.mDir, "build.gradle");
                if(build.exists()) {
                    this.mGradleFiles.add(build);
                }

                File settings = new File(this.mDir, "settings.gradle");
                if(settings.exists()) {
                    this.mGradleFiles.add(settings);
                }
            } else {
                this.mGradleFiles = Collections.emptyList();
            }
        }

        return this.mGradleFiles;
    }

    @NonNull
    public String getName() {
        if(this.mName == null) {
            this.mName = this.mClient.getProjectName(this);
        }

        return this.mName;
    }

    public void setName(@NonNull String name) {
        assert !name.isEmpty();

        this.mName = name;
    }

    public void setReportIssues(boolean reportIssues) {
        this.mReportIssues = reportIssues;
    }

    public boolean getReportIssues() {
        return this.mReportIssues;
    }

    public boolean isMergingManifests() {
        return this.mMergeManifests;
    }

    private static boolean isAospBuildEnvironment() {
        if(sAospBuild == null) {
            sAospBuild = Boolean.valueOf(getAospTop() != null);
        }

        return sAospBuild.booleanValue();
    }

    public static boolean isAospFrameworksProject(@NonNull File dir) {
        if(!dir.getPath().endsWith("core")) {
            return false;
        } else {
            File parent = dir.getParentFile();
            if(parent != null && parent.getName().equals("base")) {
                parent = parent.getParentFile();
                return parent != null && parent.getName().equals("frameworks");
            } else {
                return false;
            }
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

    private List<File> getAospJavaSourcePath() {
        List<File> sources = new ArrayList(2);
        File src = new File(this.mDir, "src");
        if(src.exists()) {
            sources.add(src);
        }

        Iterator i$ = this.getIntermediateDirs().iterator();

        while(i$.hasNext()) {
            File dir = (File)i$.next();
            File classes = new File(dir, "src");
            if(classes.exists()) {
                sources.add(classes);
            }
        }

        if(sources.isEmpty()) {
            this.mClient.log((Throwable)null, "Warning: Could not find sources or generated sources for project %1$s", new Object[]{this.getName()});
        }

        return sources;
    }

    private List<File> getAospJavaClassPath() {
        List<File> classDirs = new ArrayList(1);
        Iterator i$ = this.getIntermediateDirs().iterator();

        while(i$.hasNext()) {
            File dir = (File)i$.next();
            File classes = new File(dir, "classes");
            if(classes.exists()) {
                classDirs.add(classes);
            } else {
                classes = new File(dir, "classes.jar");
                if(classes.exists()) {
                    classDirs.add(classes);
                }
            }
        }

        if(classDirs.isEmpty()) {
            this.mClient.log((Throwable)null, "No bytecode found: Has the project been built? (%1$s)", new Object[]{this.getName()});
        }

        return classDirs;
    }

    private List<File> getIntermediateDirs() {
        List<File> intermediates = new ArrayList();
        String moduleName = this.mDir.getName();
        String top = getAospTop();
        String[] outFolders = new String[]{top + "/out/host/common/obj", top + "/out/target/common/obj", getAospHostOut() + "/obj", getAospProductOut() + "/obj"};
        String[] moduleClasses = new String[]{"APPS", "JAVA_LIBRARIES"};
        String[] arr$ = outFolders;
        int len$ = outFolders.length;


        for (String out : outFolders) {
            assert new File(out.replace('/', File.separatorChar)).exists() : out;
            for (String moduleClass : moduleClasses) {
                String path = out + '/' + moduleClass + '/' + moduleName
                        + "_intermediates"; //$NON-NLS-1$
                File file = new File(path.replace('/', File.separatorChar));
                if (file.exists()) {
                    intermediates.add(file);
                }
            }
        }

        return intermediates;
    }

    private void extractAospMinSdkVersion() {
        boolean found = false;
        File makefile = new File(this.mDir, "Android.mk");
        if(makefile.exists()) {
            try {
                List<String> lines = Files.readLines(makefile, Charsets.UTF_8);
                Pattern p = Pattern.compile("LOCAL_SDK_VERSION\\s*:=\\s*(.*)");
                Iterator i$ = lines.iterator();

                while(i$.hasNext()) {
                    String line = (String)i$.next();
                    line = line.trim();
                    Matcher matcher = p.matcher(line);
                    if(matcher.matches()) {
                        found = true;
                        String version = matcher.group(1);
                        if(version.equals("current")) {
                            this.mManifestMinSdk = findCurrentAospVersion();
                        } else {
                            this.mManifestMinSdk = SdkVersionInfo.getVersion(version, this.mClient.getTargets());
                        }
                        break;
                    }
                }
            } catch (IOException var9) {
                this.mClient.log(var9, (String)null, new Object[0]);
            }
        }

        if(!found) {
            this.mManifestMinSdk = findCurrentAospVersion();
        }

    }

    private static AndroidVersion findCurrentAospVersion() {
        if(sCurrentVersion == null) {
            File apiDir = new File(getAospTop(), "frameworks/base/api".replace('/', File.separatorChar));
            File[] apiFiles = apiDir.listFiles();
            if(apiFiles == null) {
                sCurrentVersion = AndroidVersion.DEFAULT;
                return sCurrentVersion;
            }

            int max = 1;
            File[] arr$ = apiFiles;
            int len$ = apiFiles.length;

            for(int i$ = 0; i$ < len$; ++i$) {
                File apiFile = arr$[i$];
                String name = apiFile.getName();
                int index = name.indexOf(46);
                if(index > 0) {
                    String base = name.substring(0, index);
                    if(Character.isDigit(base.charAt(0))) {
                        try {
                            int version = Integer.parseInt(base);
                            if(version > max) {
                                max = version;
                            }
                        } catch (NumberFormatException var11) {
                            ;
                        }
                    }
                }
            }

            sCurrentVersion = new AndroidVersion(max, (String)null);
        }

        return sCurrentVersion;
    }

    @Nullable
    public Boolean dependsOn(@NonNull String artifact) {
        Iterator i$;
        File file;
        String name;
        Project dependency;
        Boolean b;
        if("com.android.support:support-v4".equals(artifact)) {
            if(this.mSupportLib == null) {
                i$ = this.getJavaLibraries().iterator();

                label61: {
                    do {
                        if(!i$.hasNext()) {
                            break label61;
                        }

                        file = (File)i$.next();
                        name = file.getName();
                    } while(!name.equals("android-support-v4.jar") && !name.startsWith("support-v4-"));

                    this.mSupportLib = Boolean.valueOf(true);
                }

                if(this.mSupportLib == null) {
                    i$ = this.getDirectLibraries().iterator();

                    while(i$.hasNext()) {
                        dependency = (Project)i$.next();
                        b = dependency.dependsOn(artifact);
                        if(b != null && b.booleanValue()) {
                            this.mSupportLib = Boolean.valueOf(true);
                            break;
                        }
                    }
                }

                if(this.mSupportLib == null) {
                    this.mSupportLib = Boolean.valueOf(false);
                }
            }

            return this.mSupportLib;
        } else if(!"com.android.support:appcompat-v7".equals(artifact)) {
            return null;
        } else {
            if(this.mAppCompat == null) {
                i$ = this.getJavaLibraries().iterator();

                while(i$.hasNext()) {
                    file = (File)i$.next();
                    name = file.getName();
                    if(name.startsWith("appcompat-v7-")) {
                        this.mAppCompat = Boolean.valueOf(true);
                        break;
                    }
                }

                if(this.mAppCompat == null) {
                    i$ = this.getDirectLibraries().iterator();

                    while(i$.hasNext()) {
                        dependency = (Project)i$.next();
                        b = dependency.dependsOn(artifact);
                        if(b != null && b.booleanValue()) {
                            this.mAppCompat = Boolean.valueOf(true);
                            break;
                        }
                    }
                }

                if(this.mAppCompat == null) {
                    this.mAppCompat = Boolean.valueOf(false);
                }
            }

            return this.mAppCompat;
        }
    }

    @Nullable
    public List<String> getApplicableDensities() {
        if (mCachedApplicableDensities == null) {
            // Use the gradle API to set up relevant densities. For example, if the
            // build.gradle file contains this:
            // android {
            //     defaultConfig {
            //         resConfigs "nodpi", "hdpi"
            //     }
            // }
            // ...then we should only enforce hdpi densities, not all these others!
            if (isGradleProject() && getGradleProjectModel() != null &&
                    getCurrentVariant() != null) {
                Set<String> relevantDensities = Sets.newHashSet();
                Variant variant = getCurrentVariant();
                List<String> variantFlavors = variant.getProductFlavors();
                AndroidProject gradleProjectModel = getGradleProjectModel();

                addResConfigsFromFlavor(relevantDensities, null,
                        getGradleProjectModel().getDefaultConfig());
                for (ProductFlavorContainer container : gradleProjectModel.getProductFlavors()) {
                    addResConfigsFromFlavor(relevantDensities, variantFlavors, container);
                }

                // Are there any splits that specify densities?
                if (relevantDensities.isEmpty()) {
                    AndroidArtifact mainArtifact = variant.getMainArtifact();
                    Collection<AndroidArtifactOutput> outputs = mainArtifact.getOutputs();
                    for (AndroidArtifactOutput output : outputs) {
                        for (OutputFile file : output.getOutputs()) {
                            final String DENSITY_NAME = OutputFile.FilterType.DENSITY.name();
                            if (file.getFilterTypes().contains(DENSITY_NAME)) {
                                for (FilterData data : file.getFilters()) {
                                    if (DENSITY_NAME.equals(data.getFilterType())) {
                                        relevantDensities.add(data.getIdentifier());
                                    }
                                }
                            }
                        }
                    }
                }

                if (!relevantDensities.isEmpty()) {
                    mCachedApplicableDensities = Lists.newArrayListWithExpectedSize(10);
                    for (String density : relevantDensities) {
                        String folder = ResourceFolderType.DRAWABLE.getName() + '-' + density;
                        mCachedApplicableDensities.add(folder);
                    }
                    Collections.sort(mCachedApplicableDensities);
                } else {
                    mCachedApplicableDensities = Collections.emptyList();
                }
            } else {
                mCachedApplicableDensities = Collections.emptyList();
            }
        }

        return mCachedApplicableDensities.isEmpty() ? null : mCachedApplicableDensities;
    }

    @NonNull
    public Map<String, String> getSuperClassMap() {
        if(this.mSuperClassMap == null) {
            this.mSuperClassMap = this.mClient.createSuperClassMap(this);
        }

        return this.mSuperClassMap;
    }

    private static void addResConfigsFromFlavor(@NonNull Set<String> relevantDensities, @Nullable List<String> variantFlavors, @NonNull ProductFlavorContainer container) {
        ProductFlavor flavor = container.getProductFlavor();
        if((variantFlavors == null || variantFlavors.contains(flavor.getName())) && !flavor.getResourceConfigurations().isEmpty()) {
            Iterator i$ = flavor.getResourceConfigurations().iterator();

            while(i$.hasNext()) {
                String densityName = (String)i$.next();
                Density density = Density.getEnum(densityName);
                if(density != null && density.isRecommended() && density != Density.NODPI && density != Density.ANYDPI) {
                    relevantDensities.add(densityName);
                }
            }
        }

    }

    @NonNull
    public ResourceVisibilityLookup getResourceVisibility() {
        if(this.mResourceVisibility == null) {
            if(this.isGradleProject()) {
                AndroidProject project = this.getGradleProjectModel();
                Variant variant = this.getCurrentVariant();
                if(project != null && variant != null) {
                    this.mResourceVisibility = this.mClient.getResourceVisibilityProvider().get(project, variant);
                } else if(this.getGradleLibraryModel() != null) {
                    try {
                        this.mResourceVisibility = this.mClient.getResourceVisibilityProvider().get(this.getGradleLibraryModel());
                    } catch (Exception var4) {
                        ;
                    }
                }
            }

            if(this.mResourceVisibility == null) {
                this.mResourceVisibility = ResourceVisibilityLookup.NONE;
            }
        }

        return this.mResourceVisibility;
    }

}
