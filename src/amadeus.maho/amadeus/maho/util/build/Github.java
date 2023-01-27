package amadeus.maho.util.build;

import java.nio.file.Path;
import java.util.Map;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.util.dynamic.DynamicObject;
import amadeus.maho.util.link.http.HttpApi;
import amadeus.maho.util.link.http.HttpHelper;
import amadeus.maho.util.link.http.HttpSetting;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.util.link.http.HttpHelper.RequestType.*;

@HttpApi.Endpoint(value = "https://api.github.com", headers = { HttpHelper.Header.Accept, "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28" })
public interface Github extends HttpApi {
    
    record Owner(Github github, String name) {
        
        @Extension.Operator("GET")
        public Repo repo(final String repo) = { github(), name(), repo };
        
    }
    
    record Repo(Github github, String owner, String name) {
        
        @Extension.Operator("~")
        public DynamicObject listReleases() = github().listReleases(owner(), name());
        
        @Extension.Operator(">")
        public DynamicObject createRelease(final Releases releases) = github().createRelease(owner(), name(), releases);
        
        @Extension.Operator("-")
        public DynamicObject deleteRelease(final int release_id) = github().deleteRelease(owner(), name(), release_id);
        
        public DynamicObject uploadReleaseAsset(final int release_id, final String name, final String label, final Path file) = github().uploadReleaseAsset(owner(), name(), release_id, name, label, file);
        
        @Extension.Operator("GET")
        DynamicObject getBranch(final String branch) = github().getBranch(owner(), name(), branch);
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class Releases {
        
        // The name of the tag.
        @Default
        String tag_name;
        
        @Default
        // Specifies the commitish value that determines where the Git tag is created from. Can be any branch or commit SHA. Unused if the Git tag already exists. Default: the repository's default branch (usually master).
        String target_commitish;
        
        @Default
        // The name of the release.
        String name;
        
        @Default
        // Text describing the contents of the tag.
        String body;
        
        // true to create a draft (unpublished) release, false to create a published one. Default: false
        boolean draft;
        
        // true to identify the release as a prerelease. false to identify the release as a full release. Default: false
        boolean prerelease;
        
        // If specified, a discussion of the specified category is created and linked to the release. The value must be a category that already exists in the repository. For more information,
        // see "Managing categories for discussions in your repository."
        String discussion_category_name;
        
        // Whether to automatically generate the name and body for this release. If name is specified, the specified name will be used; otherwise, a name will be automatically generated.
        // If body is specified, the body will be pre-pended to the automatically generated notes. Default: false
        boolean generate_release_notes;
        
        // Specifies whether this release should be set as the latest release for the repository. Drafts and prereleases cannot be set as latest. Defaults to true for newly published releases.
        // legacy specifies that the latest release should be determined based on the release creation date and higher semantic version. Default: true
        // Can be one of: true, false, legacy
        String make_latest = "true";
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class ReleasesID {
        
        // The unique identifier of the release.
        @Default
        int release_id;
        
    }
    
    HttpApi.Adapter.Fallback adapter = { HttpApi.Adapter.Default.instance(), HttpApi.Adapter.Json.instance() };
    
    @Request(method = GET, path = "/repos/{owner}/{repo}/releases")
    DynamicObject listReleases(String owner, String repo);
    
    @Request(method = POST, path = "/repos/{owner}/{repo}/releases")
    DynamicObject createRelease(String owner, String repo, @Body Releases releases);
    
    @Request(method = DELETE, path = "/repos/{owner}/{repo}/releases/{release_id}")
    DynamicObject deleteRelease(String owner, String repo, int release_id);
    
    @Request(endpoint = "https://uploads.github.com", method = POST, path = "/repos/{owner}/{repo}/releases/{release_id}/assets{?name,label}")
    DynamicObject uploadReleaseAsset(String owner, String repo, int release_id, String name, String label, @Body Path file);
    
    @Request(method = GET, path = "/repos/{owner}/{repo}/branches/{branch}")
    DynamicObject getBranch(String owner, String repo, String branch);
    
    @Extension.Operator("GET")
    default Owner owner(final String owner) = { this, owner };
    
    static HttpSetting authorization(final String token = Environment.local().lookup("amadeus.maho.github.token", DebugHelper.breakpointWhenDebug("<missing>")))
            = { HttpSetting.withBaseHeaders(Map.of(HttpHelper.Header.Authorization, "Bearer " + token)) };
    
    static Github make(final HttpSetting setting = authorization()) = HttpApi.make(setting, adapter);
    
}
