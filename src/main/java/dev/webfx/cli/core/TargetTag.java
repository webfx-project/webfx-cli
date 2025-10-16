package dev.webfx.cli.core;

import java.util.*;

/**
 * @author Bruno Salmon
 */
public enum TargetTag {

    PLATFORM_PARTITION(),
    JAVA            ("java", PLATFORM_PARTITION, Platform.JRE, Platform.GWT, Platform.J2CL, Platform.TEAVM),
    JRE             ("jre", JAVA, Platform.JRE),
    OPENJFX         ("openjfx", JRE), // => DESKTOP
    GLUON           ("gluon", OPENJFX), // => native
    VERTX           ("vertx", JRE),
    WEB             ("web", JAVA, Platform.GWT, Platform.J2CL, Platform.TEAVM), // => BROWSER
    ELEMENTAL2      ("elemental2", WEB), // => BROWSER
    GWT             ("gwt", ELEMENTAL2, Platform.GWT),
    J2CL            ("j2cl", ELEMENTAL2, Platform.J2CL),
    TEAVM           ("teavm", ELEMENTAL2, Platform.TEAVM),
    EMUL            ("emul", WEB),
    POLYFILL        ("polyfill", TEAVM),

    ARCH_PARTITION  (),
    SHARED          ("shared", ARCH_PARTITION),
    SERVER          ("server", SHARED), // => JRE
    CLIENT          ("client", SHARED), // TODO: automatically assign CLIENT when code is using javafx.graphics, etc... Ex: kbs-magiclink-application
    BACKOFFICE      ("backoffice", CLIENT),
    FRONTOFFICE     ("frontoffice", CLIENT),

    VIEWER_PARTITION(), // => CLIENT
    DESKTOP         ("desktop", VIEWER_PARTITION), // => JRE
    MOBILE          ("mobile", VIEWER_PARTITION),
    BROWSER         ("browser", VIEWER_PARTITION),

    WEB_TECHNO_PARTITION(), // => WEB
    HTML            ("html", WEB_TECHNO_PARTITION),
    SVG             ("svg", WEB_TECHNO_PARTITION),

    ;

    private static TargetTag directImpliedTag(TargetTag tag) {
        return switch (tag) {
            case SERVER, DESKTOP -> JRE;
            case OPENJFX -> DESKTOP;
            case WEB -> BROWSER;
            case VIEWER_PARTITION -> CLIENT;
            //case WEB: return GWT;
            case WEB_TECHNO_PARTITION -> WEB;
            case VERTX -> SERVER;
            default -> null;
        };
    }

    private final String tagName;
    private final TargetTag parentTag;
    private List<Platform> supportedPlatforms;
    private final TargetTag partitionTag;
    private final int partitionDepth;
    private TargetTag[] transitiveImpliedTags;
    private Map<TargetTag, TargetTag> deepestMembers;

    TargetTag() {
        this(null, null);
    }

    TargetTag(String tagName, TargetTag parentTag) {
        this(tagName, parentTag, (Platform[]) null);
    }

    TargetTag(String tagName, TargetTag parentTag, Platform... supportedPlatforms) {
        this.parentTag = parentTag;
        this.tagName = tagName;
        if (supportedPlatforms != null)
            this.supportedPlatforms = Arrays.asList(supportedPlatforms);
        TargetTag partitionTag = this;
        int partitionDepth = 0;
        while (partitionTag.getParentTag() != null) {
            partitionDepth++;
            partitionTag = partitionTag.getParentTag();
        }
        this.partitionTag = partitionTag;
        this.partitionDepth = partitionDepth;
    }

    public TargetTag getParentTag() {
        return parentTag;
    }

    String getTagName() {
        return tagName;
    }

    public boolean isPlatformTag() {
        if (parentTag != null)
            return parentTag.isPlatformTag();
        return this == PLATFORM_PARTITION;
    }

    List<Platform> getSupportedPlatforms() {
        if (supportedPlatforms == null) {
            ArrayList<Platform> platforms = new ArrayList<>(Arrays.asList(Platform.values()));
            restrictPlatforms(platforms, this, true);
            supportedPlatforms = platforms;
        }
        return supportedPlatforms;
    }

    private static void restrictPlatforms(List<Platform> platforms, TargetTag tag, boolean includeImpliedTags) {
        if (tag.supportedPlatforms != null)
            platforms.removeIf(platform -> !tag.isPlatformSupported(platform));
        else if (tag.parentTag != null)
            restrictPlatforms(platforms, tag.parentTag, true);
        if (includeImpliedTags)
            for (TargetTag impliedTag : tag.getTransitiveImpliedTags())
                restrictPlatforms(platforms, impliedTag, false);
    }

    public TargetTag[] getTransitiveImpliedTags() {
        if (transitiveImpliedTags == null) {
            List<TargetTag> tags = new ArrayList<>();
            addTransitiveImpliedTags(tags, this);
            tags.remove(this);
            transitiveImpliedTags = tags.toArray(TargetTag[]::new);
        }
        return transitiveImpliedTags;
    }

    private static void addTransitiveImpliedTags(Collection<TargetTag> transitiveTags, TargetTag tag) {
        TargetTag directImpliedTag = directImpliedTag(tag);
        if (directImpliedTag != null && !transitiveTags.contains(directImpliedTag)) {
            transitiveTags.add(directImpliedTag);
            addTransitiveImpliedTags(transitiveTags, directImpliedTag);
        }
        if (tag.getParentTag() != null)
            addTransitiveImpliedTags(transitiveTags, tag.getParentTag());
    }

    public TargetTag getPartitionTag() {
        return partitionTag;
    }

    public int getPartitionDepth() {
        return partitionDepth;
    }

    boolean isPlatformSupported(Platform platform) {
        return getSupportedPlatforms().contains(platform);
    }

    boolean isPlatformCompatible(TargetTag requestedTag) {
        return requestedTag.getSupportedPlatforms().stream().anyMatch(this::isPlatformSupported);
    }

    int gradeCompatibility(TargetTag requestedTag) {
        return isPlatformCompatible(requestedTag) ? gradePartitionCompatibility(requestedTag) : -1;
    }

    private int gradePartitionCompatibility(TargetTag requestedTag) {
        // We don't accept a tag that is deeper than the requested tag (related to the same partition tag). For example,
        // webfx-platform-shutdown-gluon is for gluon only; it shouldn't be taken for openjfx, which should take
        // webfx-platform-shutdown-java (less deep) instead.
        if (getPartitionTag() == requestedTag.getPartitionTag() && getPartitionDepth() > requestedTag.getPartitionDepth())
            return -1;
        int grade = 0;
        Map<TargetTag, TargetTag> deepestMembers = getDeepestPartitionMembers();
        for (Map.Entry<TargetTag, TargetTag> requestedEntry : requestedTag.getDeepestPartitionMembers().entrySet()) {
            TargetTag deepestMember = deepestMembers.get(requestedEntry.getKey());
            if (deepestMember != null) {
                TargetTag deepestRequestedMember = requestedEntry.getValue();
                if (!deepestMember.belongsToSamePartitionBranch(deepestRequestedMember))
                    return -1;
                grade += deepestMember.getPartitionDepth();
            }
        }
        return grade;
    }

    private Map<TargetTag, TargetTag> getDeepestPartitionMembers() {
        if (deepestMembers == null) {
            deepestMembers = new HashMap<>();
            deepestMembers.put(getPartitionTag(), this);
            for (TargetTag impliedTag : getTransitiveImpliedTags()) {
                TargetTag partitionTag = impliedTag.getPartitionTag();
                TargetTag deepestMember = deepestMembers.get(partitionTag);
                if (deepestMember == null || impliedTag.getPartitionDepth() > deepestMember.getPartitionDepth())
                    deepestMembers.put(partitionTag, impliedTag);
            }
        }
        return deepestMembers;
    }

    private boolean belongsToSamePartitionBranch(TargetTag otherMember) {
        if (otherMember.getPartitionTag() != getPartitionTag())
            return false;
        TargetTag deepest = otherMember.getPartitionDepth() > getPartitionDepth() ? otherMember : this;
        TargetTag lightest = deepest == otherMember ? this : otherMember;
        while (true) {
            if (deepest == lightest)
                return true;
            if (deepest.getParentTag() == null)
                return false;
            deepest = deepest.getParentTag();
        }
    }

    public static TargetTag fromTagName(String tagName) {
        return Arrays.stream(TargetTag.values())
                .filter(tag -> tagName.equals(tag.getTagName()))
                .findFirst()
                .orElse(null);
    }

    public static TargetTag[] parseTags(String text, boolean skipFirstToken) {
        return Arrays.stream(text.split("-"))
                .skip(skipFirstToken ? 1 : 0) // Ignoring the first tag for module names (ex: gluon-image-issue-application-gwt => for gwt, not gluon)
                .map(TargetTag::fromTagName)
                .filter(Objects::nonNull)
                .toArray(TargetTag[]::new);
    }
}
