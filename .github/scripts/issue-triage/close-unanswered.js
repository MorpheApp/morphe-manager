// Closes issues where the author did not reply within 7 days after missing details were requested.
// A reply is a comment by the author, or an edit of the issue that was not made by a bot, after the request.
// If the author replied, the "waiting on author" label is removed so a maintainer can look at the issue.
// Only requests posted by the triage workflow are handled, not labels added by maintainers.

const { LABEL_WAITING_ON_AUTHOR, MISSING_INFO_MARKER, removeLabelIfPresent } = require('./common.js');

const CLOSE_AFTER_DAYS = 7;

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const live = process.env.TRIAGE_MODE === 'live';
  const cutoff = Date.now() - CLOSE_AFTER_DAYS * 24 * 60 * 60 * 1000;

  const issues = await github.paginate(github.rest.issues.listForRepo, {
    owner,
    repo,
    state: 'open',
    labels: LABEL_WAITING_ON_AUTHOR,
    per_page: 100,
  });

  for (const issue of issues) {
    if (issue.pull_request) continue;

    const comments = await github.paginate(github.rest.issues.listComments, {
      owner,
      repo,
      issue_number: issue.number,
      per_page: 100,
    });

    const request = comments
      .filter((comment) => comment.user && comment.user.type === 'Bot' && comment.body.startsWith(MISSING_INFO_MARKER))
      .pop();
    if (!request) {
      core.info(`#${issue.number} has no request for details from the triage workflow, skipping`);
      continue;
    }

    const requestedAt = Date.parse(request.created_at);
    const authorCommented = comments.some(
      (comment) => Date.parse(comment.created_at) > requestedAt && comment.user && comment.user.id === issue.user.id
    );

    // Edits by the workflow itself, such as wrapping screenshots, are not a reply.
    const { repository } = await github.graphql(
      `query($owner: String!, $repo: String!, $number: Int!) {
        repository(owner: $owner, name: $repo) {
          issue(number: $number) {
            userContentEdits(last: 20) {
              nodes {
                editedAt
                editor {
                  __typename
                }
              }
            }
          }
        }
      }`,
      { owner, repo, number: issue.number }
    );
    const authorEdited = repository.issue.userContentEdits.nodes.some(
      (edit) => Date.parse(edit.editedAt) > requestedAt && edit.editor && edit.editor.__typename !== 'Bot'
    );

    if (authorCommented || authorEdited) {
      core.info(`#${issue.number} was answered, removing the label for a maintainer to look at`);
      if (live) {
        await removeLabelIfPresent(github, owner, repo, issue.number, LABEL_WAITING_ON_AUTHOR);
      }
      continue;
    }

    if (requestedAt > cutoff) {
      core.info(`#${issue.number} was asked for details less than ${CLOSE_AFTER_DAYS} days ago`);
      continue;
    }

    core.info(`Closing #${issue.number}, no reply in ${CLOSE_AFTER_DAYS} days`);
    if (!live) continue;

    await github.rest.issues.update({
      owner,
      repo,
      issue_number: issue.number,
      state: 'closed',
      state_reason: 'not_planned',
    });
  }
};
