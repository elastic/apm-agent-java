# -*- coding: utf-8 -*-

import lib


def test_major_releases(release_index):
    ret = lib.major_releases(release_index)
    expected_titles = ["Java Agent version 1.x",
                       "Java Agent version 0.8.x",
                       "Java Agent version 0.7.x"]
    expected_titles.sort()
    ret_titles = []
    for tag in ret:
        ret_titles.append(tag["title"])
    ret_titles.sort()
    assert ret_titles == expected_titles


def test_sub_releases(release_sub):
    ret = lib.sub_releases(release_sub)
    expected_ret = ['1.9.0',
                    '1.8.0',
                    '1.7.0',
                    '1.6.1',
                    '1.6.0',
                    '1.5.0',
                    '1.4.0',
                    '1.3.0',
                    '1.2.0',
                    '1.1.0',
                    '1.0.1',
                    '1.0.0',
                    '1.0.0.rc1']
    assert ret == expected_ret


def test_strip_file_from_url():
    ret = lib.strip_file_from_url("http://foo.com/myfile.html")
    assert ret == "http://foo.com"


def test_strip_file_from_url_slash():
    ret = lib.strip_file_from_url("http://foo.com/myfile.html",
                                  add_trailing_slash=True)
    assert ret == "http://foo.com/"
