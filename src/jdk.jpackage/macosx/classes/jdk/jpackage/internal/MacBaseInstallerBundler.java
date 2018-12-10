/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jpackage.internal;

import jdk.jpackage.internal.resources.MacResources;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jdk.jpackage.internal.StandardBundlerParam.*;

public abstract class MacBaseInstallerBundler extends AbstractBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MacResources");

    // This could be generalized more to be for any type of Image Bundler
    public static final BundlerParamInfo<MacAppBundler> APP_BUNDLER =
            new StandardBundlerParam<>(
            I18N.getString("param.app-bundler.name"),
            I18N.getString("param.app-bundle.description"),
            "mac.app.bundler",
            MacAppBundler.class,
            params -> new MacAppBundler(),
            (s, p) -> null);

    public final BundlerParamInfo<File> APP_IMAGE_BUILD_ROOT =
            new StandardBundlerParam<>(
            I18N.getString("param.app-image-build-root.name"),
            I18N.getString("param.app-image-build-root.description"),
            "mac.app.imageRoot",
            File.class,
            params -> {
                File imageDir = IMAGES_ROOT.fetchFrom(params);
                if (!imageDir.exists()) imageDir.mkdirs();
                try {
                    return Files.createTempDirectory(
                            imageDir.toPath(), "image-").toFile();
                } catch (IOException e) {
                    return new File(imageDir, getID()+ ".image");
                }
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<File> CONFIG_ROOT =
            new StandardBundlerParam<>(
            I18N.getString("param.config-root.name"),
            I18N.getString("param.config-root.description"),
            "configRoot",
            File.class,
            params -> {
                File imagesRoot =
                        new File(BUILD_ROOT.fetchFrom(params), "macosx");
                imagesRoot.mkdirs();
                return imagesRoot;
            },
            (s, p) -> null);

    public static final BundlerParamInfo<String> SIGNING_KEY_USER =
            new StandardBundlerParam<>(
            I18N.getString("param.signing-key-name.name"),
            I18N.getString("param.signing-key-name.description"),
            Arguments.CLIOptions.MAC_SIGNING_KEY_NAME.getId(),
            String.class,
            params -> "",
            null);

    public static final BundlerParamInfo<String> SIGNING_KEYCHAIN =
            new StandardBundlerParam<>(
            I18N.getString("param.signing-keychain.name"),
            I18N.getString("param.signing-keychain.description"),
            Arguments.CLIOptions.MAC_SIGNING_KEYCHAIN.getId(),
            String.class,
            params -> "",
            null);

    public static final BundlerParamInfo<String> INSTALLER_NAME =
            new StandardBundlerParam<> (
            I18N.getString("param.installer-name.name"),
            I18N.getString("param.installer-name.description"),
            "mac.installerName",
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);
                if (nm == null) return null;

                String version = VERSION.fetchFrom(params);
                if (version == null) {
                    return nm;
                } else {
                    return nm + "-" + version;
                }
            },
            (s, p) -> s);

    protected void validateAppImageAndBundeler(
            Map<String, ? super Object> params)
            throws ConfigException, UnsupportedPlatformException {
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) != null) {
            File applicationImage = PREDEFINED_APP_IMAGE.fetchFrom(params);
            if (!applicationImage.exists()) {
                throw new ConfigException(
                        MessageFormat.format(I18N.getString(
                                "message.app-image-dir-does-not-exist"),
                                PREDEFINED_APP_IMAGE.getID(),
                                applicationImage.toString()),
                        MessageFormat.format(I18N.getString(
                                "message.app-image-dir-does-not-exist.advice"),
                                PREDEFINED_APP_IMAGE.getID()));
            }
            if (APP_NAME.fetchFrom(params) == null) {
                throw new ConfigException(
                        I18N.getString("message.app-image-requires-app-name"),
                        I18N.getString(
                            "message.app-image-requires-app-name.advice"));
            }
            if (IDENTIFIER.fetchFrom(params) == null) {
                throw new ConfigException(
                        I18N.getString("message.app-image-requires-identifier"),
                        I18N.getString(
                            "message.app-image-requires-identifier.advice"));
            }
        } else {
            APP_BUNDLER.fetchFrom(params).validate(params);
        }
    }

    protected File prepareAppBundle(
            Map<String, ? super Object> p, boolean pkg) {
        File predefinedImage = StandardBundlerParam.getPredefinedAppImage(p);
        if (predefinedImage != null) {
            return predefinedImage;
        }
        File appImageRoot = APP_IMAGE_BUILD_ROOT.fetchFrom(p);
        if (pkg) {
            // create pkg in dmg
            return new MacPkgBundler().bundle(p, appImageRoot);
        } else {
            return APP_BUNDLER.fetchFrom(p).doBundle(p, appImageRoot, true);
        }
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();

        results.addAll(MacAppBundler.getAppBundleParameters());
        results.addAll(Arrays.asList(
                APP_BUNDLER,
                CONFIG_ROOT,
                APP_IMAGE_BUILD_ROOT,
                PREDEFINED_APP_IMAGE
        ));

        return results;
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    public static String findKey(String key, String keychainName,
            boolean verbose) {
        if (Platform.getPlatform() != Platform.MAC) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos)) {
            List<String> searchOptions = new ArrayList<>();
            searchOptions.add("security");
            searchOptions.add("find-certificate");
            searchOptions.add("-c");
            searchOptions.add(key);
            searchOptions.add("-a");
            if (keychainName != null && !keychainName.isEmpty()) {
                searchOptions.add(keychainName);
            }

            ProcessBuilder pb = new ProcessBuilder(searchOptions);

            IOUtils.exec(pb, verbose, false, ps);
            Pattern p = Pattern.compile("\"alis\"<blob>=\"([^\"]+)\"");
            Matcher m = p.matcher(baos.toString());
            if (!m.find()) {
                Log.error("Did not find a key matching '" + key + "'");
                return null;
            }
            String matchedKey = m.group(1);
            if (m.find()) {
                Log.error("Found more than one key matching '"  + key + "'");
                return null;
            }
            Log.debug("Using key '" + matchedKey + "'");
            return matchedKey;
        } catch (IOException ioe) {
            Log.verbose(ioe);
            return null;
        }
    }
}
