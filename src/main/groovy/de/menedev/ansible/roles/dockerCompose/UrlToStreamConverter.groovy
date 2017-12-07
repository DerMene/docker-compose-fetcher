package de.menedev.ansible.roles.dockerCompose

class UrlToStreamConverter {
    public InputStream getStream(URL url) {
        return url.openStream()
    }
}
