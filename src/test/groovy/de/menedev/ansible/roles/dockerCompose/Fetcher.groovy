package de.menedev.ansible.roles.dockerCompose

import spock.lang.Specification

class UpdaterSpec extends Specification {

    def "Run fetcher and save results"() {
        given:

        def converter = Stub(UrlToStreamConverter)

        converter.getStream(_ as URL) >> {
            new ByteArrayInputStream("".bytes)
        }

        def updater = new Updater(converter)

        System.getProperty("user.home") + "/ansible-role/vars/main.yml"
        def url = new URL("https://api.github.com/repos/docker/compose/releases")
        def stream = new ByteArrayOutputStream()

        when:
        def releases = updater.getReleases(url)

        // limit for testing
        releases = releases.subList(0, 3)
        updater.update(stream, releases)

        then:
        noExceptionThrown()
        new String(stream.toByteArray()).contains("docker_compose_latest")
        new String(stream.toByteArray()).contains("docker_compose_checksums")
        new String(stream.toByteArray()).contains("Linux")
        new String(stream.toByteArray()).contains("Windows")
        new String(stream.toByteArray()).contains("Darwin")
        new String(stream.toByteArray()).contains("latest")
    }
}
