package dev.webfx.cli.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
public final class Target {

    private final ProjectModule module;
    private final List<TargetTag> tags;
    private final List<TargetTag> platformTags;
    private List<Platform> supportedPlatforms;

    Target(ProjectModule module) {
        this(module, TargetTag.parseTags(module.getName(), true));
    }

    public Target(TargetTag... tags) {
        this(null, tags);
    }

    private Target(ProjectModule module, TargetTag[] tags) {
        this.module = module;
        this.tags = Arrays.stream(tags).collect(Collectors.toUnmodifiableList());
        platformTags = this.tags.stream()
                // filtering platform tags (ex: GWT, JRE, GLUON, VERTX, ...) from this tag and transitive implied tags (ex: SERVER => JRE)
                .flatMap(tag -> Stream.concat(Stream.of(tag), Stream.of(tag.getTransitiveImpliedTags())))
                .filter(Objects::nonNull)
                .filter(TargetTag::isPlatformTag)
                .collect(Collectors.toUnmodifiableList());
    }

    ProjectModule getModule() {
        return module;
    }

    List<TargetTag> getTags() {
        return tags;
    }

    public boolean hasTag(TargetTag tag) {
        return tags.contains(tag);
    }

    public List<Platform> getSupportedPlatforms() {
       if (supportedPlatforms == null) {
            supportedPlatforms = Arrays.stream(Platform.values()).filter(this::isPlatformSupported).collect(Collectors.toUnmodifiableList());
        }
        return supportedPlatforms;
    }

    public boolean isPlatformSupported(Platform platform) {
        // Note: if there is no platform-specific tag, then it's a generic module that supports all platforms
        return platformTags.isEmpty() || platformTags.stream().allMatch(tag -> tag.isPlatformSupported(platform));
    }

    public boolean isAnyPlatformSupported(Platform platform) {
        // Note: if there is no platform-specific tag, then it's a generic module that supports any platform
        return platformTags.isEmpty() || platformTags.stream().anyMatch(tag -> tag.isPlatformSupported(platform));
    }

    public boolean isMonoPlatform() {
        return getSupportedPlatforms().size() == 1;
    }

    public boolean isMonoPlatform(Platform platform) {
        return isMonoPlatform() && getSupportedPlatforms().get(0) == platform;
    }

    int gradeTargetMatch(Target requestedTarget) {
        int grade = 0;
        for (TargetTag requestedTag : requestedTarget.getTags()) {
            List<TargetTag> tags = getTags();
            for (TargetTag tag : tags) {
                int tagGrade = tag.gradeCompatibility(requestedTag);
                // If tagGrade is negative, it's likely that this tag is incompatible with the requested target,
                // unless we find another tag in the list that is the requested tag.
                // For example: webfx-kit-platform-audio-openjfx-gwt provides the same implementation for both OpenJFX
                // and GWT. If the requested tag is OpenJFX, then tagGrade will be negative when grading GWT, but
                // this negative grade should be ignored in this case, because the module has also the tag OpenJFX.
                if (tagGrade < 0 && tags.size() > 1 && tags.stream().anyMatch(t -> t == requestedTag))
                    tagGrade = 0; // ignoring the negative grade in this case
                if (tagGrade < 0) // Otherwise for all other cases, if the grade is negative,
                    return tagGrade; // then it's incompatible with the requested target, so we return that negative grade
                grade += tagGrade;
            }
        }
        //System.out.println(this + ".gradeTargetMatch(" + requestedTarget + ") = " + grade);
        return grade;
    }

    @Override
    public String toString() {
        return "Target{" +
               "module=" + module +
               ", tags=" + tags +
               '}';
    }
}
