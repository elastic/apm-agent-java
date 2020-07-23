#!/usr/bin/env bash
case  $1  in
    bare)
        git tag | sort -V|tail -1|sed s/\n//
        ;;
    ver)
        git tag | sort -V |tail -1| sed s/v//
        ;;
    dot_x)
        git tag|sort -V|tail -1 |cut -f1 -d "."|awk '{print $1".x"}'
        ;;
esac
