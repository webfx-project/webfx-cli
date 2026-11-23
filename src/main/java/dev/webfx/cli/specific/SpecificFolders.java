package dev.webfx.cli.specific;

/**
 * @author Bruno Salmon
 */
public interface SpecificFolders {

    String USER_WEBFX = ".webfx";
    String USER_M2_REPOSITORY = ".m2/repository";

    String SRC = "src";
    String SRC_MAIN = "src/main";
    String SRC_MAIN_JAVA = "src/main/java";
    String SRC_MAIN_WEBFX = "src/main/webfx";
    String SRC_TEST_JAVA = "src/test/java";
    String SRC_MAIN_RESOURCES = "src/main/resources";

    String TARGET = "target";

    String META_INF_SERVICES = "META-INF/services";
    String META_INF_WEBFX = "META-INF/webfx";

    /**
     * Where the merged i18n dictionaries used to be generated, and the package of webfx-extras-i18n's own API
     * (I18n, Dictionary, I18nKeys) — hence a split package in every executable carrying dictionaries, refused on
     * the module path. Kept only so that the file left there by an earlier CLI can be deleted.
     */
    String LEGACY_RES_I18N_FOLDER = "dev/webfx/extras/i18n";
    /** Where they are generated now, by the same .exe convention as dev.webfx.platform.meta.exe. */
    String RES_I18N_FOLDER = LEGACY_RES_I18N_FOLDER + "/exe";
}
