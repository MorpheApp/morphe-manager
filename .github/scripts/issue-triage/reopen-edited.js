// Reopens a bug report that was closed for its version, after the author edits it and its version is supported.
// Only issues that the triage closed are reopened. An issue that a maintainer closed again is left closed.

const { VERSION_CLOSE_MARKER, isFromTemplate } = require('./common.js');
const { checkVersions, hasVersionField } = require('./check-versions.js');

const BOT_LOGIN = 'github-actions[bot]';

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const { issue, sender, changes } = context.payload;
  const live = process.env.TRIAGE_MODE === 'live';

  if (issue.state !== 'closed' || issue.state_reason !== 'not_planned') {
    core.info(`#${issue.number} is not closed as not planned`);
    return;
  }
  if (!changes || !changes.body) {
    core.info(`#${issue.number} body was not changed`);
    return;
  }
  if (sender.login !== issue.user.login) {
    core.info(`#${issue.number} was edited by ${sender.login}, not by its author`);
    return;
  }
  if (!isFromTemplate(issue.body) || !hasVersionField(issue.body)) {
    core.info(`#${issue.number} is no longer a bug report form`);
    return;
  }

  const events = await github.paginate(github.rest.issues.listEvents, {
    owner,
    repo,
    issue_number: issue.number,
    per_page: 100,
  });
  const lastClose = events.filter((event) => event.event === 'closed').pop();
  if (!lastClose || !lastClose.actor || lastClose.actor.login !== BOT_LOGIN) {
    core.info(`#${issue.number} was not last closed by the triage`);
    return;
  }

  const comments = await github.paginate(github.rest.issues.listComments, {
    owner,
    repo,
    issue_number: issue.number,
    per_page: 100,
  });
  const closedForVersions = comments.some(
    (comment) => comment.user.login === BOT_LOGIN && comment.body.startsWith(VERSION_CLOSE_MARKER)
  );
  if (!closedForVersions) {
    core.info(`#${issue.number} was not closed for its version`);
    return;
  }

  const problems = await checkVersions({ github, core, owner, repo, issue });
  if (problems.length > 0) {
    core.info(`#${issue.number} still has an unsupported version, leaving it closed`);
    return;
  }

  core.info(`#${issue.number} version is now supported, reopening it`);
  if (!live) return;
  await github.rest.issues.update({ owner, repo, issue_number: issue.number, state: 'open' });
};
