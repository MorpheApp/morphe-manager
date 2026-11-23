// Applies the triage decision from the analyze job to an issue.
// The decision comes from text written by the issue author, so it is treated as untrusted:
// every field is validated, and comments are built only from fixed text and issue numbers.

const {
  LABEL_POSSIBLE_DUPLICATE,
  LABEL_WAITING_ON_AUTHOR,
  LABEL_QUESTION,
  LABEL_BUG,
  LABEL_FEATURE,
  MISSING_INFO_MARKER,
  VERSION_CLOSE_MARKER,
  POSSIBLE_DUPLICATE_PREFIX,
  ensureLabel,
  removeLabelIfPresent,
  escapeHtml,
} = require('./common.js');

const PATCHES_ISSUE_COMMENT = [
  'This issue is about the patches, not about Morphe.',
  '',
  'Please open an issue in the Morphe Patches repository instead: https://github.com/MorpheApp/morphe-patches/issues/new/choose',
].join('\n');

const MAX_DUPLICATES = 3;

// bug: Summary, feat: Summary
const TITLE_PATTERN = /^(bug|feat): \S.{2,115}$/;

const MISSING_INFO_TEXT = {
  steps_to_reproduce: 'Steps to reproduce the bug (Step 1. ... Step 2. ...)',
  versions: 'The version of Morphe, as shown in Settings > System > About, and the version of the app you are patching',
  logs: 'The Morphe logs, exported with Settings > System > About > Export debug logs',
  screenshots: 'A screenshot or screen recording that shows the bug',
  one_bug_per_issue: 'Only one bug per issue. Please edit this issue to keep one bug, and open a new issue for each of the others',
};

function validateTitle(title) {
  if (typeof title !== 'string') return null;
  const trimmed = title.trim();
  if (!TITLE_PATTERN.test(trimmed)) return null;
  // No mentions, links or HTML.
  if (/[@<>]|https?:|www\./i.test(trimmed)) return null;
  return trimmed;
}

async function validateDuplicates(github, owner, repo, issueNumber, numbers) {
  if (!Array.isArray(numbers)) return [];

  const valid = [];
  for (const number of numbers) {
    if (valid.length >= MAX_DUPLICATES) break;
    if (!Number.isInteger(number) || number <= 0 || number === issueNumber) continue;
    if (valid.some((issue) => issue.number === number)) continue;

    let candidate;
    try {
      candidate = (await github.rest.issues.get({ owner, repo, issue_number: number })).data;
    } catch (error) {
      if (error.status !== 404 && error.status !== 410) throw error;
      console.log(`Duplicate candidate #${number} does not exist`);
      continue;
    }
    if (candidate.pull_request) {
      console.log(`Duplicate candidate #${number} is a pull request`);
      continue;
    }
    // A duplicate must be the older issue.
    if (number > issueNumber) {
      console.log(`Duplicate candidate #${number} is newer than #${issueNumber}`);
      continue;
    }
    valid.push(candidate);
  }
  return valid;
}

function possibleDuplicateComment(duplicates) {
  return [
    duplicates.length === 1
      ? `${POSSIBLE_DUPLICATE_PREFIX} an existing issue:`
      : `${POSSIBLE_DUPLICATE_PREFIX} existing issues:`,
    '',
    ...duplicates.map((number) => `- #${number}`),
    '',
    'If it is, please close this issue and add a 👍 to the existing issue instead.',
  ].join('\n');
}

function missingInfoComment(missing) {
  return [
    MISSING_INFO_MARKER,
    'Thanks for the report. Please edit the issue to add:',
    '',
    ...missing.map((key) => `- ${MISSING_INFO_TEXT[key]}`),
  ].join('\n');
}

/**
 * Version problems found by check-template.js. They are built only from fixed text.
 */
function parseVersionProblems(json) {
  const problems = JSON.parse(json || '[]');
  if (!Array.isArray(problems) || !problems.every((problem) => typeof problem === 'string')) {
    throw new Error(`Invalid version problems: ${json}`);
  }
  return problems;
}

/**
 * Comments the version problems, and closes the issue as not planned unless closeIssue is false.
 * A closed issue is reopened by reopen-edited.js when the author edits it and the version is then supported.
 */
async function commentVersionProblems(github, owner, repo, issueNumber, versionProblems, closeIssue) {
  const body = closeIssue
    ? [
        VERSION_CLOSE_MARKER,
        ...versionProblems,
        'Once this is fixed, edit this issue with the new version and it will be reopened automatically. ' +
          'Please do not open a new issue for this bug.',
      ].join('\n\n')
    : versionProblems.join('\n\n');
  await github.rest.issues.createComment({ owner, repo, issue_number: issueNumber, body });
  if (!closeIssue) return;
  await github.rest.issues.update({
    owner,
    repo,
    issue_number: issueNumber,
    state: 'closed',
    state_reason: 'not_planned',
  });
}

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const issueNumber = Number(process.env.ISSUE_NUMBER);
  const live = process.env.TRIAGE_MODE === 'live';
  const versionProblems = parseVersionProblems(process.env.VERSION_PROBLEMS);

  const { data: issue } = await github.rest.issues.get({ owner, repo, issue_number: issueNumber });
  // An issue that is closed still gets its title cleaned up and its type label,
  // but no comments, deletion or duplicate changes.
  const open = issue.state === 'open';
  if (!open) {
    core.info(`#${issueNumber} is closed, only the title and type label are changed`);
  }

  let decision = null;
  if (process.env.DECISION) {
    try {
      decision = JSON.parse(process.env.DECISION);
    } catch (error) {
      // Fails the run, but an issue with version problems is still closed below.
      core.setFailed(`Could not parse the triage decision: ${error.message}`);
    }
  }

  // Without a decision, such as when the analyze job failed, an issue with version problems is still closed.
  if (!decision) {
    if (!open || versionProblems.length === 0) {
      core.info('No triage decision, nothing to do');
      return;
    }
    core.warning(`No triage decision, only closing #${issueNumber} for its version`);
    await core.summary
      .addHeading(`Triage of #${issueNumber}${live ? '' : ' (dry run)'}`, 3)
      .addList(['No triage decision. Close for its version'])
      .write();
    if (live) await commentVersionProblems(github, owner, repo, issueNumber, versionProblems, true);
    return;
  }

  // Issues about the patches are closed with a link to the patches repository, nothing else is done.
  if (open && decision.patches_issue === true) {
    await core.summary
      .addHeading(`Triage of #${issueNumber}${live ? '' : ' (dry run)'}`, 3)
      .addRaw(`Current title: ${escapeHtml(issue.title)}`, true)
      .addBreak()
      .addRaw(`Reasoning: ${escapeHtml(decision.reasoning || '(none)')}`, true)
      .addList(['Issue about the patches: comment and close'])
      .write();
    if (!live) {
      core.info('Dry run. Set the repository variable ISSUE_TRIAGE_MODE to "live" to apply changes.');
      return;
    }
    await github.rest.issues.createComment({ owner, repo, issue_number: issueNumber, body: PATCHES_ISSUE_COMMENT });
    await github.rest.issues.update({
      owner,
      repo,
      issue_number: issueNumber,
      state: 'closed',
      state_reason: 'not_planned',
    });
    return;
  }

  const labels = issue.labels.map((label) => (typeof label === 'string' ? label : label.name));
  const actions = [];

  // Title. The prefix must match the type.
  let title = validateTitle(decision.title);
  if (title && (decision.type === 'bug' || decision.type === 'feature')) {
    title = title.replace(/^(bug|feat):/, decision.type === 'bug' ? 'bug:' : 'feat:');
  }
  if (decision.title && !title) {
    core.warning(`Ignoring invalid title: ${JSON.stringify(decision.title)}`);
  }
  if (title && title !== issue.title) {
    actions.push({
      description: `Rename to: ${title}`,
      run: () => github.rest.issues.update({ owner, repo, issue_number: issueNumber, title }),
    });
  }

  // Type
  const typeLabels = { bug: LABEL_BUG, feature: LABEL_FEATURE, question: LABEL_QUESTION };
  const typeLabel = typeLabels[decision.type];
  if (typeLabel && !labels.includes(typeLabel)) {
    actions.push({
      description: `Add label: ${typeLabel}`,
      run: () => github.rest.issues.addLabels({ owner, repo, issue_number: issueNumber, labels: [typeLabel] }),
    });
  }
  if (typeLabel === LABEL_BUG || typeLabel === LABEL_FEATURE) {
    const otherLabel = typeLabel === LABEL_BUG ? LABEL_FEATURE : LABEL_BUG;
    if (labels.includes(otherLabel)) {
      actions.push({
        description: `Remove label: ${otherLabel}`,
        run: () => removeLabelIfPresent(github, owner, repo, issueNumber, otherLabel),
      });
    }
  }

  // Duplicates. A sure duplicate is closed without a comment, after the other changes.
  // Possible duplicates get a comment and stay open for a maintainer to decide.
  const [duplicateOf] = open && Number.isInteger(decision.duplicate_of)
    ? await validateDuplicates(github, owner, repo, issueNumber, [decision.duplicate_of])
    : [];
  const possibleDuplicates = !open || duplicateOf
    ? []
    : (await validateDuplicates(github, owner, repo, issueNumber, decision.possible_duplicates)).map(
        (candidate) => candidate.number
      );

  if (possibleDuplicates.length > 0) {
    actions.push({
      description: `Possible duplicate of ${possibleDuplicates.map((n) => `#${n}`).join(', ')}`,
      run: async () => {
        await ensureLabel(github, owner, repo, LABEL_POSSIBLE_DUPLICATE);
        await github.rest.issues.createComment({
          owner,
          repo,
          issue_number: issueNumber,
          body: possibleDuplicateComment(possibleDuplicates),
        });
        await github.rest.issues.addLabels({
          owner,
          repo,
          issue_number: issueNumber,
          labels: [LABEL_POSSIBLE_DUPLICATE],
        });
      },
    });
  }

  // Missing information. Only for bugs that are not duplicates and are not closed for their version.
  const missing = Array.isArray(decision.missing_info)
    ? [...new Set(decision.missing_info.filter((key) => key in MISSING_INFO_TEXT))]
    : [];
  if (
    open &&
    decision.type === 'bug' &&
    !duplicateOf &&
    possibleDuplicates.length === 0 &&
    versionProblems.length === 0 &&
    missing.length > 0
  ) {
    actions.push({
      description: `Ask for: ${missing.join(', ')}`,
      run: async () => {
        await github.rest.issues.createComment({
          owner,
          repo,
          issue_number: issueNumber,
          body: missingInfoComment(missing),
        });
        await github.rest.issues.addLabels({
          owner,
          repo,
          issue_number: issueNumber,
          labels: [LABEL_WAITING_ON_AUTHOR],
        });
      },
    });
  }

  // Version problems are commented after the possible duplicates, so the author sees both.
  // A sure duplicate is closed as a duplicate instead of as not planned.
  if (open && versionProblems.length > 0) {
    actions.push({
      description: `${duplicateOf ? 'Comment' : 'Close for'} version problems: ${versionProblems.join(' ')}`,
      run: () => commentVersionProblems(github, owner, repo, issueNumber, versionProblems, !duplicateOf),
    });
  }

  if (duplicateOf) {
    actions.push({
      description: `Close as a duplicate of #${duplicateOf.number}`,
      run: () =>
        github.graphql(
          `mutation($issueId: ID!, $duplicateId: ID!) {
            closeIssue(input: { issueId: $issueId, stateReason: DUPLICATE, duplicateIssueId: $duplicateId }) {
              clientMutationId
            }
          }`,
          { issueId: issue.node_id, duplicateId: duplicateOf.node_id }
        ),
    });
  }

  const summary = core.summary
    .addHeading(`Triage of #${issueNumber}${live ? '' : ' (dry run)'}`, 3)
    .addRaw(`Current title: ${escapeHtml(issue.title)}`, true)
    .addBreak()
    .addRaw(`Reasoning: ${escapeHtml(decision.reasoning || '(none)')}`, true);
  if (actions.length === 0) {
    summary.addRaw('<p>No changes.</p>', true);
  } else {
    summary.addList(actions.map((action) => escapeHtml(action.description)));
  }
  await summary.write();

  if (!live) {
    core.info('Dry run. Set the repository variable ISSUE_TRIAGE_MODE to "live" to apply changes.');
    return;
  }

  for (const action of actions) {
    core.info(action.description);
    await action.run();
  }
};

module.exports.validateTitle = validateTitle;
