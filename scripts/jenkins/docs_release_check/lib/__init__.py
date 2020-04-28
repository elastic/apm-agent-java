# -*- coding: utf-8 -*-
import urllib.parse
import requests
import argparse
import copy
import re
import sys

from bs4 import BeautifulSoup
from rich.progress import track
from rich.console import Console


def parse_args():
    """
    Parse command-line arguments

    Returns
    -------
    argparse.Namespace
        An object which contains all the command-line arguments accessible
        by property, ala `args.foo`
    """
    parser = argparse.ArgumentParser(description='Produce a CHANGELOG from a \
        repository adhering to Conventional Commits standards')
    parser.add_argument("--url",
                        help="URL to parse for docs. This should be the "
                             "top-level page for changelogs, "
                             "linking off to other other pages.\nExample: "
                             "https://www.elastic.co/guide/en/apm/agent/java/current/release-notes.html",  # noqa E501
                        required=True)
    parser.add_argument("--release",
                        help="Release to search for. e.g. 1.15.0",
                        required=True)
    parser.add_argument("--verbose",
                        action="store_true",
                        help="Versbose debugging",
                        required=False)
    return parser.parse_args()


def fetch(url):
    """
    Fetch a remote URL and return the contents of the page

    Parameters
    ----------
    url : str
        The URL to fetch.
        e.g. https://www.elastic.co/guide/en/apm/agent/java/current/release-notes.html  # noqa E501

    Returns
    -------
    str
        The contents of the requested web page

    Raises
    ------
    requests.exceptions.HTTPError
        If the request failed an exception will be raised.
    """
    r = requests.get(url)
    if r.status_code == r.status_code != requests.codes.ok:
        r.raise_for_status()
    return r.text


def major_releases(page):
    """
    Take a main page and return a list containing the major releases HTML
    elements.

    Parameters
    ----------
    page : str
        The HTML to analyze

    Returns
    -------
    list
        A list containing the release elements
    """
    soup = BeautifulSoup(page, "html.parser")
    chapter = soup.find("div", class_="chapter")
    links = chapter.find_all("a")
    return list(filter(lambda x: x.next_element.startswith('Java'), links))


def sub_releases(page):
    """
    Take a specific branch release page and extract all the releases

    Parameters
    ----------
    page : str
        The HTML page to analyze. Should be a specific branch page of releases,
        such as https://www.elastic.co/guide/en/apm/agent/java/current/release-notes-1.x.html  # noqa E501

    Returns
    -------
    list
        A list containing the releases where each release is a string.

    Raises
    ------
    Exception
        A generic exception is raised if the page cannot be parsed or is not a release page.
    """
    versions = list()
    soup = BeautifulSoup(page, "html.parser")
    release_candidates = soup.find_all(
        "a", id=re.compile(r"release-notes-\d+\.\d+\.\d+"))
    for candidate in release_candidates:
        versions.append(candidate["id"].split("-").pop())
    return versions


def strip_file_from_url(url, add_trailing_slash=False):
    """
    Take a URL and return the result without the filename that
    is being requested

    Parameters
    ----------
    url : str
        The URL to replace

    Returns
    -------
    str
        The URL without the filename.

    Raises
    ------
    Exception
        If the URL does not contain a file ending in HTML an exception
        will be thrown.
    """
    o = urllib.parse.urlparse(url)
    p = copy.deepcopy(list(o))
    path_elements = o.path.split("/")
    if not path_elements[-1].endswith(".html"):
        raise Exception("URL does not to go HTML file")
    p[2] = "/".join((path_elements[:-1]))
    u = urllib.parse.urlunparse(p)
    if add_trailing_slash:
        return u + "/"
    else:
        return u


def entrypoint():
    args = parse_args()
    console = Console()
    page = fetch(args.url)
    releases = major_releases(page)
    release_site_dir = strip_file_from_url(args.url, add_trailing_slash=True)
    found_versions = list()
    for release in track(releases, description="Analyzing releases"):
        sub_page = fetch(release_site_dir + release["href"])
        found_versions.extend(sub_releases(sub_page))
    if args.verbose:
        import pprint
        pprint.pprint(found_versions)
    if args.release in found_versions:
        console.print(":thumbs_up: Release found")
        sys.exit(0)
    else:
        console.print(":boom: Release could not be found")
        sys.exit(1)
