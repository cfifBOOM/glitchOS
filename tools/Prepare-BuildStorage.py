#!/usr/bin/env python3
"""Retired image creator: the project uses Ubuntu's existing ext4 on G:."""
import sys


def main():
    print('This image-creation command has been retired. No files were changed.')
    print('Use the existing Ubuntu filesystem on G:; see BUILDING.md.')
    return 2


if __name__ == '__main__':
    sys.exit(main())
