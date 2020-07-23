#!/usr/bin/env bash
case  $1  in
    bare)
        git tag | sort -V | tail -1 | tr -d '\n'
        ;;
    ver)
        git tag | sort -V | tail -1 | sed s/v// | tr -d '\n'
        ;;
    dot_x)
        git tag | sort -V | tail -1 | cut -f1 -d "." | awk '{print $1".x"}' | tr -d '\n'
        ;;
esac
