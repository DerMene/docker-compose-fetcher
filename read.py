import requests
import json
import hashlib

from ruamel.yaml import YAML
from ruamel.yaml.scalarstring import SingleQuotedScalarString, DoubleQuotedScalarString

try:
    from packaging.version import parse
except ImportError:
    from pip._vendor.packaging.version import parse

URL_PATTERN = 'https://pypi.python.org/pypi/{package}/json'

role_folder = "menedev.docker-compose/"
in_file = role_folder + ".travis.yml"
out_file = role_folder + "new.travis.yml"
in_vars_main = role_folder + "vars/main.yml"
out_vars_main = role_folder + "vars/main.yml"

def asset_to_os(asset, release):
    name = asset['name']
    return 'Linux' if 'Linux' in name else 'Windows' if 'Windows' in name else 'Darwin' if 'Darwin' in name else False

# https://stackoverflow.com/questions/28774852/pypi-api-how-to-get-stable-package-version
def get_pip_versions(package, url_pattern=URL_PATTERN, min_version=parse("2.0")):
    req = requests.get(url_pattern.format(package=package))
    if req.status_code != requests.codes.ok:
        raise ValueError("Request return code is not OK")

    response_body = json.loads(req.text.encode(req.encoding))
    if 'releases' not in response_body:
        raise ValueError("Request returns unknown format (releases missing)")

    releases = response_body['releases']
    versions = [v for v in releases if parse(v) >= min_version]
    return versions

def asset_to_version(asset, release):
    return release['name']

def asset_to_checksum(asset, release):
    # return "a"
    get = requests.get(asset[u'browser_download_url'])
    get.raw.decode_content = True

    hash_object = hashlib.sha256()
    for chunk in get:
        hash_object.update(chunk)

    hex_dig = hash_object.hexdigest()
    return "sha256:%s" % hex_dig

def skip_asset(asset, release):
    name = asset['name']
    content_type = asset[u'content_type']
    if not (content_type == u'application/octet-stream' and 'Linux' in name
            or content_type == u'application/octet-stream' and 'Darwin' in name
            or content_type in [u'application/x-msdownload', u'application/x-ms-dos-executable'] and 'Windows' in name):
        return True
    else:
        return False

def get_github_versions(organization, project, known_releases):
    url_pattern = "https://api.github.com/repos/{organization}/{project}/releases"
    req = requests.get(url_pattern.format(organization=organization, project=project))
    if req.status_code != requests.codes.ok:
        raise ValueError("Request return code is not OK")

    response_body = json.loads(req.text.encode(req.encoding))
    if not isinstance(response_body, list):
        raise ValueError("Request returns unknown format (not a list)")

    if any('assets' not in release for release in response_body):
        raise ValueError("Request returns unknown format (assets missing)")

    releases = {}

    for release in response_body:
        for asset in release['assets']:
            os = asset_to_os(asset, release)
            if skip_asset(asset, release):
                continue
            os_releases = releases.get(os, {})
            version = asset_to_version(asset, release)
            version_hash = os_releases.get(version, known_releases.get(os, {}).get(version, None))
            if not version_hash:
                print "no hash for %s %s, downloading" % (os, version)
                version_hash = asset_to_checksum(asset, release)

            os_releases[version] = version_hash
            releases[os] = os_releases
            print "found %s %s" % (os, version)
    return releases


def quote(string):
    return DoubleQuotedScalarString(string)


if __name__ == '__main__':

    yaml = YAML()
    yaml.explicit_start = True
    yaml.preserve_quotes = True

    config = None

    vars_main = {}

    with open(in_vars_main, 'r') as stream:
        try:
            vars_main = yaml.load(stream)
        except yaml.YAMLError as exc:
            print(exc)

    known_releases = vars_main.get("docker_compose_checksums", {})

    with open("menedev.docker-compose/.travis.yml", 'r') as stream:
        try:
            config = yaml.load(stream)
        except yaml.YAMLError as exc:
            print(exc)

    github = get_github_versions('docker', 'compose', known_releases)
    ansible = get_pip_versions('ansible')

    # add github releases one by one to preserve comments in yaml
    for os, os_version in github.items():
        for version, digest in os_version.items():
            old = known_releases[os].get(version, None)
            if old is None or old != digest:
                known_releases[os][quote(version)] = quote(digest)

    # delete entries from known values that are not in github releases
    for os, os_version in known_releases.items():
        for version, digest in os_version.items():
            other = github[os].get(version, None)
            if other is None:
                del known_releases[os][version]

    key_latest = quote('latest')
    key_latest_stable = quote('latest_stable')

    # for each os, look for the latest version
    for os, os_version in known_releases.items():
        latest = max(os_version.keys(), key=lambda v: parse(v))
        os_version[key_latest] = os_version[latest]

        latest_stable = max([v for v in os_version.keys() if not parse(v).is_prerelease], key=lambda v: parse(v))
        os_version[key_latest_stable] = os_version[latest_stable]


    # order the lists of releases
    #  1. latest
    #  2. latest_stable
    #  ... desc versions
    for os, os_version in known_releases.items():
        latest = os_version[key_latest]
        latest_stable = os_version[key_latest_stable]
        del os_version[key_latest]
        del os_version[key_latest_stable]
        version_hash_list = os_version.items()
        version_hash_list = sorted(version_hash_list, key=lambda version_hash: parse(version_hash[0]), reverse=True)
        del os_version[:]
        os_version[key_latest] = latest
        os_version[key_latest_stable] = latest_stable
        for version, digest in version_hash_list:
            os_version[version] = digest


    with open(out_vars_main, 'w') as saveTo:
        yaml.dump(vars_main, saveTo)

    # Clear list but preserve comments
    del config['env'][:]

    max_ansible = max(ansible, key=lambda v: parse(v))
    for v2 in [max_ansible, '2.0.0']:
        for v1 in github.keys():
            config['env'].append("PROJECT_VERSION=" + v1 + " ANSIBLE_VERSION=" + v2)

    with open("exampletravis.out.yml", 'w') as saveTo:
        yaml.dump(config, saveTo)

    print(config)
