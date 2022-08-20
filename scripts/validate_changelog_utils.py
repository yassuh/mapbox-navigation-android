import re

changelog_diff_regex = "^([\s]*)diff --git a(.*)\/CHANGELOG.md b(.*)\/CHANGELOG.md"
changelog_filename = "CHANGELOG.md"
any_diff_substring = "diff --git"
diff_file_start_regex = "^([\s]*)\@\@(.*)\@\@"
pr_link_regex = "\[#\d+]\(https:\/\/github\.com\/mapbox\/mapbox-navigation-android\/pull\/\d+\)"

def is_line_added(line):
    return line.strip().startswith('+')

def remove_plus(line):
    return line.strip()[1:]

def group_by_versions(lines):
    groups = {}
    group = []
    group_name = ""
    for line in lines:
        if line.startswith("##") and len(line) > 2 and line[2] != '#':
            if (len(group) > 0):
                if len(group_name.strip()) > 0:
                    groups[group_name] = group
                group = []
            group_name = line
        elif len(line.strip()) > 0:
            group.append(line)
    if len(group) > 0 and len(group_name.strip()) > 0:
        groups[group_name] = group
    return groups

def extract_unreleased_group(versions):
    for version in versions.keys():
        if 'Unreleased' in version:
            return versions[version]
    raise Exception("No 'Unreleased' section in CHANGELOG")

def extract_stable_versions(versions):
    str_before_version = "Mapbox Navigation SDK "
    str_after_version = " "
    stable_versions = {}
    pattern = re.compile("[0-9]+\.[0-9]+\.[0-9]+")
    for version in versions.keys():
        beginIndex = version.find(str_before_version)
        if beginIndex == -1:
            continue
        version_name = version[(beginIndex + len(str_before_version)):]
        endIndex = version_name.find(str_after_version)
        if endIndex == -1:
            continue
        version_name = version_name[:endIndex]
        if pattern.fullmatch(version_name) != None:
            stable_versions[version_name] = versions[version]
    return stable_versions

def should_skip_changelog(response_json):
    if "labels" in response_json:
        pr_labels = response_json["labels"]
        for label in pr_labels:
            if label["name"] == "skip changelog":
                return True
    return False

def check_has_changelog_diff(diff):
    changelog_diff_matches = re.search(changelog_diff_regex, diff, re.MULTILINE)
    if not changelog_diff_matches:
        raise Exception("Add a non-empty changelog entry in a CHANGELOG.md or add a `skip changelog` label if not applicable.")

def parse_contents_url(files_response_json):
    for file_json in files_response_json:
        if file_json["filename"] == changelog_filename:
            return file_json["contents_url"]
    raise Exception("No CHANGELOG.md file in PR files")

def extract_added_lines(diff):
    changelog_diff_matches = re.search(changelog_diff_regex, diff, re.MULTILINE)
    diff_starting_at_changelog = diff[changelog_diff_matches.end():]
    first_changelog_diff = re.search(diff_file_start_regex, diff_starting_at_changelog, re.MULTILINE)
    first_changelog_diff_index = 0
    if first_changelog_diff:
        first_changelog_diff_index = first_changelog_diff.end()
    last_reachable_index = diff_starting_at_changelog.find(any_diff_substring, first_changelog_diff_index)
    if last_reachable_index == -1:
        last_reachable_index = len(diff_starting_at_changelog)
    diff_searchable = diff_starting_at_changelog[first_changelog_diff_index:last_reachable_index]

    diff_lines = diff_searchable.split('\n')
    return list(map(remove_plus, list(filter(is_line_added, diff_lines))))

def check_contains_pr_link(added_lines):
    for added_line in added_lines:
        pr_link_matches = re.search(pr_link_regex, added_line)
        if not pr_link_matches:
            raise Exception("The changelog entry \"" + added_line + "\" should contain a link to the original PR that matches `" + pr_link_regex + "`")

def check_version_section(content, added_lines):
    lines = content.split("\n")
    versions = group_by_versions(lines)
    unreleased_group = extract_unreleased_group(versions)
    stable_versions = extract_stable_versions(versions)

    for added_line in added_lines:
        if added_line not in unreleased_group:
            raise Exception(added_line + " should be placed in 'Unreleased' section")

        for stable_version in stable_versions:
            if added_line in stable_versions[stable_version]:
                raise Exception("The changelog entry \"" + added_line + "\" is already contained in " + stable_version + " changelog.")