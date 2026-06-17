#!/usr/bin/env bash
#
# ship.sh: a minimal release helper script.
#
#   ship.sh new [-f] [-P] [-b <base>] [<version>]  create branch release/<version>, push
#   ship.sh release [-f] [-P] <version>            tag tip of release/<version> branch, push
#   ship.sh ci <version>                           CI-only; mark commit for shipping and emit dotenv vars
#
# Environment overrides:
#   SHIP_REMOTE          remote to fetch/push          (default: origin)
#   SHIP_DEFAULT_BRANCH  base branch for `new`         (default: master)
#   SHIP_TAG_PREFIX      release tag prefix            (default: v)
#   SHIP_NOTES_REF       git-notes ref for build attrs (default: refs/notes/proton/attrs)
#   SHIP_NOTES_TRAILER   trailer key for the build pipeline id (default: Build-Pipeline)
#
set -euo pipefail

REMOTE="${SHIP_REMOTE:-origin}"
DEFAULT_BRANCH="${SHIP_DEFAULT_BRANCH:-master}"
TAG_PREFIX="${SHIP_TAG_PREFIX:-v}"
NOTES_REF="${SHIP_NOTES_REF:-refs/notes/proton/attrs}"
NOTES_TRAILER="${SHIP_NOTES_TRAILER:-Build-Pipeline}"
CARGO_TOML="Cargo.toml"

die() { echo "ship.sh: $*" >&2; exit 1; }

usage() {
    cat >&2 <<'EOF'
usage:
  ship.sh new [-f] [-P] [-b <branch>] [<version>] create release/<version> and push
  ship.sh release [-f] [-P] <version>             tag tip of release/<version> and push
  ship.sh ci <version>                            print dotenv vars and stamp build note

options:
  -f, --force         overwrite an existing branch (new) or tag (release)
  -P, --no-push       do everything locally but skip pushing to the remote
  -b, --base <branch> base branch for `new` (default: $SHIP_DEFAULT_BRANCH / master)
EOF
    exit 1
}

# --- helpers ---------------------------------------------------------------

# Read the [package] version from Cargo.toml (first `version = "..."` line).
cargo_version() {
    awk -F'"' '/^version[[:space:]]*=/ { print $2; exit }' "$CARGO_TOML"
}

# Set the [package] version in Cargo.toml (first `version = "..."` line only).
set_cargo_version() {
    local v="$1" tmp
    tmp="$(mktemp)"
    awk -v ver="$v" '
        !done && /^version[[:space:]]*=/ { print "version = \"" ver "\""; done=1; next }
        { print }
    ' "$CARGO_TOML" > "$tmp"
    mv "$tmp" "$CARGO_TOML"
}

valid_version() {
    [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

# Resolve a branch name to a ref, preferring the remote-tracking copy.
resolve_branch() {
    local b="$1"
    if git rev-parse --verify --quiet "$REMOTE/$b" >/dev/null; then
        echo "$REMOTE/$b"
    elif git rev-parse --verify --quiet "$b" >/dev/null; then
        echo "$b"
    else
        return 1
    fi
}

# Tip of release/<version>, preferring the freshly-fetched remote ref.
release_tip() {
    resolve_branch "release/$1" || die "no branch release/$1 found on $REMOTE or locally"
}

# Highest semver among v* tags, release/* branches, and the current Cargo.toml.
highest_version() {
    {
        git tag -l "${TAG_PREFIX}*" \
            | sed -nE "s/^${TAG_PREFIX}([0-9]+\.[0-9]+\.[0-9]+)$/\1/p"
        git for-each-ref --format='%(refname:short)' \
            'refs/heads/release/*' "refs/remotes/$REMOTE/release/*" \
            | sed -nE 's#.*release/([0-9]+\.[0-9]+\.[0-9]+)$#\1#p'
        cargo_version
    } | sort -t. -k1,1n -k2,2n -k3,3n | tail -n1
}

bump_patch() {
    local IFS=. ; read -r major minor patch <<<"$1"
    echo "$major.$minor.$((patch + 1))"
}

branch_exists() {
    git show-ref --verify --quiet "refs/heads/release/$1" \
        || git ls-remote --exit-code --heads "$REMOTE" "release/$1" >/dev/null 2>&1
}

tag_exists() {
    git show-ref --tags --verify --quiet "refs/tags/${TAG_PREFIX}$1" \
        || git ls-remote --exit-code --tags "$REMOTE" "${TAG_PREFIX}$1" >/dev/null 2>&1
}

# The rc prerelease number is just the number of commits beyond the default branch.
rc_number() {
    local ref="$1" base mb n
    if base="$(resolve_branch "$DEFAULT_BRANCH")" \
        && mb="$(git merge-base "$base" "$ref" 2>/dev/null)"; then
        n="$(git rev-list --count "$mb..$ref")"
    else
        n="$(git rev-list --count "$ref")"
    fi
    (( n < 1 )) && n=1
    echo "$n"
}

# --- git notes (build attributes, git-trailer format) ----------------------

# Value of the $NOTES_TRAILER trailer recorded on a commit, if any.
note_get_pipeline() {
    git notes --ref="$NOTES_REF" show "$1" 2>/dev/null \
        | sed -nE "s/^${NOTES_TRAILER}:[[:space:]]*(.+)$/\1/p" \
        | tail -n1 || true
}

# Record the pipeline id on a commit: drop any existing $NOTES_TRAILER line,
# keep every other trailer, then append the (possibly original) pipeline id.
note_set_pipeline() {
    local ref="$1" id="$2" rest
    rest="$(git notes --ref="$NOTES_REF" show "$ref" 2>/dev/null \
              | grep -vE "^${NOTES_TRAILER}:[[:space:]]*" || true)"
    {
        [[ -n "$rest" ]] && printf '%s\n' "$rest"
        printf '%s: %s\n' "$NOTES_TRAILER" "$id"
    } | git notes --ref="$NOTES_REF" add -f -F - "$ref"
}

# --- remote sync -----------------------------------------------------------

fetch_origin() {
    git fetch --prune --tags "$REMOTE" >/dev/null 2>&1 \
        || echo "ship.sh: warning: could not fetch from $REMOTE (using local state)" >&2
    git fetch "$REMOTE" "+$NOTES_REF:$NOTES_REF" >/dev/null 2>&1 || true
}

push_notes() {
    git push "$REMOTE" "$NOTES_REF" >/dev/null 2>&1 \
        || echo "ship.sh: warning: could not push notes ($NOTES_REF)" >&2
}

# --- option parsing --------------------------------------------------------

parse_args() {
    FORCE=0; NOPUSH=0; BRANCH=""; VERSION=""
    local got_version=0
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -f|--force)    FORCE=1 ;;
            -P|--no-push)  NOPUSH=1 ;;
            -b|--base)     shift; [[ $# -gt 0 ]] || die "$1 requires a branch name"
                           BRANCH="$1" ;;
            --base=*)      BRANCH="${1#*=}" ;;
            --)            ;;
            -*)            die "unknown option: $1" ;;
            *)             [[ $got_version -eq 1 ]] && die "unexpected argument: $1"
                           VERSION="$1"; got_version=1 ;;
        esac
        shift
    done
}

# --- verbs -----------------------------------------------------------------

cmd_new() {
    parse_args "$@"
    fetch_origin

    if [[ -z "$VERSION" ]]; then
        local prev; prev="$(highest_version)"
        [[ -n "$prev" ]] || die "could not determine a base version"
        VERSION="$(bump_patch "$prev")"
        echo "ship.sh: no version given, using $VERSION (bumped from $prev)" >&2
    fi
    valid_version "$VERSION" || die "invalid version: $VERSION (expected MAJOR.MINOR.PATCH)"

    local branch="release/$VERSION" base="${BRANCH:-$DEFAULT_BRANCH}" base_ref
    base_ref="$(resolve_branch "$base")" || die "base branch '$base' not found"

    if branch_exists "$VERSION"; then
        [[ $FORCE -eq 1 ]] || die "branch $branch already exists (use -f to overwrite)"
        git branch -D "$branch" 2>/dev/null || true
        [[ $NOPUSH -eq 0 ]] && git push "$REMOTE" --delete "$branch" 2>/dev/null || true
    fi

    git checkout -b "$branch" "$base_ref"
    set_cargo_version "$VERSION"
    git add "$CARGO_TOML"
    git commit -m "Bump version to $VERSION"

    if [[ $NOPUSH -eq 1 ]]; then
        echo "ship.sh: created $branch off $base_ref (not pushed)" >&2
        return
    fi

    local push_args=(-u)
    [[ $FORCE -eq 1 ]] && push_args+=(--force)

    git push "${push_args[@]}" "$REMOTE" "$branch"
    echo "ship.sh: created $branch off $base_ref and pushed" >&2
}

cmd_release() {
    parse_args "$@"
    [[ -n "$VERSION" ]] || die "release requires a version"
    valid_version "$VERSION" || die "invalid version: $VERSION (expected MAJOR.MINOR.PATCH)"
    fetch_origin

    local tag="${TAG_PREFIX}$VERSION" ref
    ref="$(git rev-parse "$(release_tip "$VERSION")")"

    if tag_exists "$VERSION" && [[ $FORCE -ne 1 ]]; then
        die "tag $tag already exists (use -f to overwrite)"
    fi

    local force_flag=() push_force=()
    [[ $FORCE -eq 1 ]] && { force_flag=(-f); push_force=(--force); }

    # Tag message editor pre-populated with a release notes template:
    #   ```
    #   Release notes:
    #
    #   - Bug fixes and stability improvements
    #   ```
    git tag "${force_flag[@]}" -a -e \
        -m "Release notes:" \
        -m "- Bug fixes and stability improvements" \
        "$tag" "$ref"

    if [[ $NOPUSH -eq 1 ]]; then
        echo "ship.sh: tagged ${ref:0:9} as $tag (not pushed)" >&2
        return
    fi

    git push "${push_force[@]}" "$REMOTE" "refs/tags/$tag"
    echo "ship.sh: tagged ${ref:0:9} as $tag and pushed" >&2
}

cmd_ci() {
    parse_args "$@"
    [[ -n "$VERSION" ]] || die "ci requires a version"
    valid_version "$VERSION" || die "invalid version: $VERSION (expected MAJOR.MINOR.PATCH)"
    fetch_origin

    # Refuse to proceed unless HEAD is actually on release/<version>, since
    # binary promotion doesn't make sense otherwise
    local branch_ref ref n ts existing pipeline full tag
    branch_ref="$(release_tip "$VERSION")"
    ref="$(git rev-parse HEAD)"
    if ! git merge-base --is-ancestor "$ref" "$branch_ref"; then
        die "HEAD ($(git rev-parse --short HEAD)) is not on release/$VERSION; no release candidate was built for this commit"
    fi
    n="$(rc_number "$ref")"
    ts="$(git show -s --format=%cd --date=format:%y%m%d%H%M "$ref")"

    # Reuse the original build's pipeline id using the attached note.
    existing="$(note_get_pipeline "$ref")"
    pipeline="${existing:-${CI_PIPELINE_ID:-}}"
    if [[ -n "$pipeline" ]]; then
        note_set_pipeline "$ref" "$pipeline"
        [[ $NOPUSH -eq 0 ]] && push_notes
    else
        pipeline=99999 # no CI context and nothing recorded
    fi

    # If we see a tag for this commit, it isn't a prerelease, so just use
    # the existing tag version
    tag="${TAG_PREFIX}$VERSION"
    if git rev-parse -q --verify "refs/tags/$tag^{commit}" >/dev/null \
        && [[ "$(git rev-parse "refs/tags/$tag^{commit}")" == "$ref" ]]; then
        full="$VERSION"
    else
        full="$VERSION-rc.$n"
    fi

    # Used as a dotenv file for downstream pipeline jobs
    echo "RELEASE_VERSION=$VERSION"
    echo "FULL_VERSION=$full"
    echo "BUILD_VERSION=$pipeline.$ts.$n"
}

# --- dispatch --------------------------------------------------------------

[[ $# -ge 1 ]] || usage
verb="$1"; shift
case "$verb" in
    new)     cmd_new "$@" ;;
    release) cmd_release "$@" ;;
    ci)      cmd_ci "$@" ;;
    -h|--help|help) usage ;;
    *)       die "unknown command: $verb" ;;
esac
