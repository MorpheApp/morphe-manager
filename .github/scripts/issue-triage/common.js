// Shared helpers for the issue triage workflows.

const LABEL_POSSIBLE_DUPLICATE = 'possible duplicate';
const LABEL_WAITING_ON_AUTHOR = 'Waiting on author';
const LABEL_QUESTION = 'question';
const LABEL_BUG = 'bug';
const LABEL_FEATURE = 'feature request';

const LABEL_COLORS = {
  [LABEL_POSSIBLE_DUPLICATE]: 'cfd3d7',
};

// Starts the comment that asks for missing details, so close-unanswered.js can find it.
const MISSING_INFO_MARKER = '<!-- morphe-triage:missing-info -->';

// Start of the comment that lists possible duplicates, so delete-rejected-comments.js can remove the label with it.
const POSSIBLE_DUPLICATE_PREFIX = 'This issue may be a duplicate of';

const MAINTAINER_ASSOCIATIONS = ['OWNER', 'MEMBER', 'COLLABORATOR'];

// The required text field of each issue form. Every form also has an Acknowledgements section.
// The feature request description is optional, so its required Motivation field is checked instead.
const FORM_DESCRIPTION_HEADINGS = ['Bug description', 'Motivation'];

const NO_RESPONSE = '_No response_';

/**
 * Splits an issue form body into a map of field label to value.
 * Fields left empty in the form have an empty string value.
 */
function parseForm(body) {
  const fields = {};
  if (!body) return fields;

  const parts = body.replace(/\r\n/g, '\n').split(/^### +(.+?) *$/m);
  // parts[0] is any text before the first heading.
  for (let i = 1; i + 1 < parts.length; i += 2) {
    const value = parts[i + 1].trim();
    fields[parts[i]] = value === NO_RESPONSE ? '' : value;
  }
  return fields;
}

/**
 * Whether the body was created by one of the issue forms.
 * Checks for a form description field with content, and the checked acknowledgements.
 */
function isFromTemplate(body) {
  const fields = parseForm(body);

  const hasDescription = FORM_DESCRIPTION_HEADINGS.some((heading) => fields[heading]);
  if (!hasDescription) return false;

  const acknowledgements = fields['Acknowledgements'];
  if (!acknowledgements) return false;

  const checkboxes = acknowledgements.match(/^- \[[ xX]\] /gm) || [];
  const checked = acknowledgements.match(/^- \[[xX]\] /gm) || [];
  return checkboxes.length > 0 && checked.length === checkboxes.length;
}

function isMaintainer(issue) {
  return MAINTAINER_ASSOCIATIONS.includes(issue.author_association);
}

async function ensureLabel(github, owner, repo, name) {
  try {
    await github.rest.issues.getLabel({ owner, repo, name });
  } catch (error) {
    if (error.status !== 404) throw error;
    console.log(`Creating missing label "${name}"`);
    await github.rest.issues.createLabel({
      owner,
      repo,
      name,
      color: LABEL_COLORS[name] || 'ededed',
    });
  }
}

async function removeLabelIfPresent(github, owner, repo, issueNumber, name) {
  try {
    await github.rest.issues.removeLabel({ owner, repo, issue_number: issueNumber, name });
  } catch (error) {
    if (error.status !== 404) throw error;
    console.log(`Label "${name}" was not on #${issueNumber}`);
  }
}

/**
 * Comments on an issue and then deletes it, so the author gets the comment by email
 * but the issue does not stay in the repository.
 * The comment is posted with the workflow token, and the issue is deleted with
 * ISSUE_ADMIN_TOKEN, which must belong to an account with admin access to the repository.
 * Without that token, or if deleting fails, the issue is closed instead.
 */
async function commentAndDelete({ github, getOctokit, core, owner, repo, issue, comment }) {
  await github.rest.issues.createComment({ owner, repo, issue_number: issue.number, body: comment });

  const closeInstead = () =>
    github.rest.issues.update({
      owner,
      repo,
      issue_number: issue.number,
      state: 'closed',
      state_reason: 'not_planned',
    });

  const adminToken = process.env.ISSUE_ADMIN_TOKEN;
  if (!adminToken) {
    core.warning(`ISSUE_ADMIN_TOKEN is not set, closing #${issue.number} instead of deleting it`);
    await closeInstead();
    return;
  }

  try {
    const admin = getOctokit(adminToken);
    await admin.graphql(
      `mutation($id: ID!) {
        deleteIssue(input: { issueId: $id }) {
          clientMutationId
        }
      }`,
      { id: issue.node_id }
    );
  } catch (error) {
    // Fail the run so maintainers are notified that the token needs fixing.
    core.setFailed(`Could not delete #${issue.number}, closing it instead. Check ISSUE_ADMIN_TOKEN: ${error.message}`);
    await closeInstead();
    return;
  }

  core.info(`Deleted #${issue.number}`);

  // Keep a record in the job summary, as a deleted issue cannot be restored.
  await core.summary
    .addHeading(`Deleted #${issue.number}`, 3)
    .addRaw(`Author: ${escapeHtml(issue.user ? issue.user.login : 'unknown')}`, true)
    .addBreak()
    .addRaw(`Title: ${escapeHtml(issue.title)}`, true)
    .addDetails('Body', `<pre>${escapeHtml(issue.body || '')}</pre>`)
    .write();
}

function escapeHtml(text) {
  return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

module.exports = {
  LABEL_POSSIBLE_DUPLICATE,
  LABEL_WAITING_ON_AUTHOR,
  LABEL_QUESTION,
  LABEL_BUG,
  LABEL_FEATURE,
  MISSING_INFO_MARKER,
  POSSIBLE_DUPLICATE_PREFIX,
  parseForm,
  isFromTemplate,
  isMaintainer,
  ensureLabel,
  removeLabelIfPresent,
  commentAndDelete,
  escapeHtml,
};
