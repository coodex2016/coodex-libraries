/*
 * Copyright (c) 2020 coodex.org (jujus.shen@126.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.coodex.util;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.coodex.util.Common.toAbsolutePath;

/**
 * 资源扫描器，用来替换蹩脚的Common.forEach
 * <p>
 * 参数<code>coodex.resource.path</code>用来指定扩展的资源路径
 */
@Slf4j
public class ResourceScanner {
    private static final ThreadLocal<Integer> EXTRA_PATH_INDEX = new ThreadLocal<>();
    public static String KEY_RESOURCE_PATH_EXT = "coodex.resource.path";
    private final BiConsumer<URL, String> processor;
    private final BiFunction<String, String, Boolean> filter;
    private final boolean extraPath;

    private ResourceScanner(
            BiConsumer<URL, String> processor,
            BiFunction<String, String, Boolean> filter,
            boolean extraPath) {
        this.processor = processor;
        this.filter = filter == null ? (t, u) -> true : filter;
        this.extraPath = extraPath;
    }

    /**
     * @return 扫描过程中processor使用，用来判定当前扫描的包是否是在扩展路径里
     */
    public static boolean isExtraPath() {
        return EXTRA_PATH_INDEX.get() != null;
    }

    public static Integer getExtraPathIndex() {
        return EXTRA_PATH_INDEX.get();
    }

    /**
     * @return 通过<code>coodex.resource.path</code>指定的扩展资源路径绝对路径
     */
    public static List<String> getExtraResourcePath() {
        List<String> resourcePaths = new ArrayList<>();
        String param = System.getProperty(KEY_RESOURCE_PATH_EXT, "").trim();
        if (!Common.isBlank(param)) {
            String[] paths = param.split(Common.PATH_SEPARATOR);
            for (String path : paths) {
                if (!Common.isBlank(path)) {
                    resourcePaths.add(toAbsolutePath(path));
                }
            }
        }
        return resourcePaths;
    }

    public static Builder newBuilder(BiConsumer<URL, String> processor) {
        return new Builder(processor);
    }

    // 按照表达式定义转换为正则表达式
    private static Set<PathPattern> toPathPatterns(String[] paths) {
        Set<PathPattern> pathPatterns = new LinkedHashSet<>();
        if (paths != null && paths.length > 0) {
            for (String path : paths) {
                pathPatterns.add(new PathPattern(Common.trim(path) + "/"));
            }
        }
        return pathPatterns;
    }

    /**
     * @param pathPatterns pathPattern
     * @return 合并正则表达式
     */
    private static Collection<String> merge(Collection<PathPattern> pathPatterns) {
        List<String> list = new ArrayList<>();
        for (PathPattern pathPattern : pathPatterns) {
            list.add(pathPattern.path);
        }
        String[] toMerge = list.toArray(new String[0]);
        list.clear();
        // 排序
        Arrays.sort(toMerge, Comparator.comparingInt(String::length));
        for (String s : toMerge) {
            boolean exits = false;
            for (String x : list) {
                if (s.startsWith(x)) {
                    exits = true;
                    break;
                }
            }
            if (!exits) {
                list.add(s);
            }
        }
        return list;
    }

    public void scan(String... paths) {
        try {
            final Set<PathPattern> pathPatterns = toPathPatterns(paths);
            BiFunction<String, String, Boolean> resourceFilter = (root, resourceName) -> {
                boolean pathOk = false;
                for (PathPattern pathPattern : pathPatterns) {
                    if (pathPattern.pattern.matcher(resourceName).matches()) {
                        pathOk = true;
                        break;
                    }
                }
                return pathOk && filter.apply(root, resourceName);
            };
            Collection<String> merged = merge(pathPatterns);

            if (extraPath) {
                scanInExtraPath(resourceFilter, merged);
            }
            for (String path : merged) {
                path = Common.trim(path, '/');
                Enumeration<URL> resourceRoots = getClass().getClassLoader().getResources(path);
                while (resourceRoots.hasMoreElements()) {
                    URL url = resourceRoots.nextElement();
                    String urlStr = url.toString();

                    int indexOfZipMarker = urlStr.lastIndexOf('!');
                    String resourceRoot = urlStr.substring(0, urlStr.length() - path.length() - 1);


                    // 针对每一个匹配的包进行检索
                    if (indexOfZipMarker > 0) {
                        // .zip, .jar
                        String zipFile = urlStr.indexOf('!') != indexOfZipMarker
                                ? urlStr.substring(0, indexOfZipMarker) // jar文件
                                : urlStr.substring(4, indexOfZipMarker); // jar文件中的jar entry
                        String ext = zipFile.substring(zipFile.length() - 4);
                        String entryPath = "";
                        if (!".jar".equalsIgnoreCase(ext) && !".zip".equalsIgnoreCase(ext)) {
                            int i = zipFile.lastIndexOf('!');
                            entryPath = zipFile.substring(i + 1);
                            zipFile = zipFile.substring(4, i);
                        }
                        scanInZip(resourceRoot, path, resourceFilter, new URL(zipFile), entryPath);
                    } else {
                        // class path文件夹
                        scanInDir(resourceRoot, path.replace('\\', '/'), resourceFilter, new File(url.toURI()), true);
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            log.warn("resource search failed. {}.", e.getLocalizedMessage(), e);
        }
    }

    private void scanInExtraPath(BiFunction<String, String, Boolean> resourceFilter,
                                 Collection<String> merged) {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        getExtraResourcePath().forEach(root -> {
            EXTRA_PATH_INDEX.set(atomicInteger.getAndIncrement());
            try {
                merged.forEach(path -> {
                    try {
                        path = Common.trim(path, '/').replace('\\', '/');
                        File resourceRoot = new File(root + "/" + path);
                        scanInDir("file:/" + root, path, resourceFilter, resourceRoot, true);
                    } catch (Throwable th) {
                        log.warn("load from {} failed: {}", root, th.getLocalizedMessage(), th);
                    }
                });
            } finally {
                EXTRA_PATH_INDEX.remove();
            }
        });

    }

    private void scanInZip(String root,
                           String path,
                           BiFunction<String, String, Boolean> filter,
                           URL zipFile,
                           String entryPath) throws IOException {
        log.debug("Scan items in [{}]: {{}}", zipFile.toString(), path);
        try (ZipInputStream zip = new ZipInputStream(zipFile.openStream())) {
            ZipEntry entry;
            String entryContext = Common.isBlank(entryPath) ? "" : entryPath.substring(1);

            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                if (!Common.isBlank(entryContext) && !entryName.startsWith(entryContext)) continue;
                // 此包中的检索
                String resourceName = Common.isBlank(entryContext) ? entryName: entryName.substring(entryContext.length() + 1);
                if (resourceName.startsWith(path) && filter.apply(root, resourceName)) {
                    processor.accept(new URL(root + "/" + entryName), resourceName);
                }
            }
        }
    }

    private void scanInDir(String root,
                           String path,
                           BiFunction<String, String, Boolean> filter,
                           File dir,
                           boolean header) throws MalformedURLException {
        if (header)
            log.debug("Scan items in dir[{}]:[{}]", dir.getAbsolutePath(), path);
        if (dir.isDirectory() && dir.exists()) {
            for (File f : Optional.ofNullable(dir.listFiles()).orElse(new File[0])) {
                String resourceName = path + '/' + f.getName();
                if (f.isDirectory()) {
                    scanInDir(root, resourceName, filter, f, false);
                } else {
                    if (filter.apply(root, resourceName)) {
                        processor.accept(new URL(root + '/' + resourceName), resourceName);
                    }
                }
            }
        }
    }

    private static class PathPattern {
        private final Pattern pattern;
        private final String path;
        private final String originalPath;

        public PathPattern(String path) {
            this.originalPath = path;
            this.pattern = Pattern.compile(
                    "^" + Common.trim(path)
                            .replaceAll("\\.", "\\\\.")
                            .replaceAll("/\\*{2,}/", "(/|/.+/)")
                            .replaceAll("\\*{2,}", ".+")// 两个以上*匹配任意字符
                            .replaceAll("\\*", "[^/]+")
                            + ".*"
            );
            this.path = pathRoot(path);
        }

        private String pathRoot(String pattern) {
            StringBuilder builder = new StringBuilder();
            StringBuilder node = new StringBuilder();
            for (char ch : pattern.toCharArray()) {
                if (ch == '*') break;
                if (ch == '/') {
                    builder.append(node).append(ch);
                    node = new StringBuilder();
                } else
                    node.append(ch);

            }
            if (node.length() > 0)
                builder.append(node);
            return Common.trim(builder.toString());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathPattern)) return false;

            PathPattern that = (PathPattern) o;

            return originalPath.equals(that.originalPath);
        }

        @Override
        public int hashCode() {
            return originalPath.hashCode();
        }
    }

    @Accessors(fluent = true)
    @Setter
    public static class Builder {
        private final BiConsumer<URL, String> processor;
        private BiFunction<String, String, Boolean> filter;
        private boolean extraPath = false;

        private Builder(@NonNull BiConsumer<URL, String> processor) {
            this.processor = processor;
        }

        public ResourceScanner build() {
            return new ResourceScanner(processor, filter, extraPath);
        }
    }

}
