package de.menedev.ansible.roles.dockerCompose

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.security.MessageDigest

@Slf4j
class Updater {
    static private final List<String> EXECUTABLE_MIME_TYPES = ['application/octet-stream', 'application/x-ms-dos-executable']

    private final UrlToStreamConverter converter

    Updater(UrlToStreamConverter converter) {
        this.converter = converter
    }

    void update(OutputStream outputStream, List releases) {
        // name contains version, first in releases is latest release
        def latestVersion = releases[0].name
        Map result = [docker_compose_latest: latestVersion]
        result.docker_compose_checksums = ['Linux', 'Darwin', 'Windows'].collectEntries { os ->
            def assets = releases.assets
                    .findAll

            def hashes = getHashes(releases, { it.name.contains(os) &&
                    it.content_type in EXECUTABLE_MIME_TYPES })

            [(os):
                    ["latest": hashes[latestVersion]] <<
                            hashes.collectEntries {version, digest -> [(version): digest]
                    }
            ]
        }

        def mapper = new YAMLMapper()
        mapper.writeValue(outputStream, result)
        outputStream
    }

    List getReleases(URL repositoryUrl) {
        JsonSlurper slurper = new JsonSlurper()
        def releases = slurper.parse(repositoryUrl) as List
        return releases
    }

    private Map getHashes(List releases, Closure filter) {
        def downloads = releases.collectEntries { release ->
            def url = release.assets
                    .find(filter)
                    ?.browser_download_url
            [(release.name): url]
        }.findAll {k, v ->
            v} as Map<String, String>

        def hashes = downloads.collectEntries { version, url ->
            def digest = getDigest(new URL(url))
            [(version): digest]
        }
        return hashes
    }

    private String getDigest(URL url) {
        log.info "Hashing $url"
        def inputStream = converter.getStream(url)

        def digest = MessageDigest.getInstance("SHA-256")
                .digest(inputStream.bytes)
                .encodeHex()
                .toString()
        log.info "SHA-256 of $url is $digest"
        return "sha256:$digest"
    }
}
