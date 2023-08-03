package dev.webfx.cli.core;

import java.util.Arrays;

/**
 * @author Bruno Salmon
 */
public final class Target {

    private final ProjectModule module;
    private final TargetTag[] tags;

    Target(ProjectModule module) {
        this.module = module;
        this.tags = TargetTag.parseTags(module.getName(), true);
    }

    public Target(TargetTag... tags) {
        this.module = null;
        this.tags = tags;
    }

    ProjectModule getModule() {
        return module;
    }

    TargetTag[] getTags() {
        return tags;
    }

    public boolean hasTag(TargetTag tag) {
        return Arrays.asList(tags).contains(tag);
    }

    public Platform[] getSupportedPlatforms() {
        return Arrays.stream(Platform.values()).filter(this::isPlatformSupported).toArray(Platform[]::new);
    }

    public boolean isPlatformSupported(Platform platform) {
        // Note: if there is no platform-specific tag, then it's a generic module that supports all platforms
        return tags.length == 0 || Arrays.stream(tags).allMatch(tag -> tag.isPlatformSupported(platform));
    }

    public boolean isAnyPlatformSupported(Platform platform) {
        // Note: if there is no platform-specific tag, then it's a generic module that supports any platform
        return tags.length == 0 || Arrays.stream(tags).anyMatch(tag -> tag.isPlatformSupported(platform));
    }

    public boolean isMonoPlatform() {
        return getSupportedPlatforms().length == 1;
    }

    public boolean isMonoPlatform(Platform platform) {
        Platform[] supportedPlatforms = getSupportedPlatforms();
        return supportedPlatforms.length == 1 && supportedPlatforms[0] == platform;
    }

    int gradeTargetMatch(Target requestedTarget) {
        int grade = 0;
        for (TargetTag requestedTag : requestedTarget.getTags()) {
            TargetTag[] tags = getTags();
            for (TargetTag tag : tags) {
                int tagGrade = tag.gradeCompatibility(requestedTag);
                // If tagGrade is negative, it's likely that this tag is incompatible with the requested target,
                // unless we find another tag in the list that is the requested tag.
                // For example: webfx-kit-platform-audio-openjfx-gwt provides the same implementation for both OpenJFX
                // and GWT. If the requested tag is OpenJFX, then tagGrade will be negative when grading GWT, but
                // this negative grade should be ignored in this case, because the module has also the tag OpenJFX.
                if (tagGrade < 0 && tags.length > 1 && Arrays.stream(tags).anyMatch(t -> t == requestedTag))
                    tagGrade = 0; // ignoring the negative grade in this case
                if (tagGrade < 0) // Otherwise for all other cases, if the grade is negative,
                    return tagGrade; // then it's incompatible with the requested target, so we return that negative grade
                grade += tagGrade;
            }
        }
        return grade;
    }
}
