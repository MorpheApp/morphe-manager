// Wraps screenshots and screen recordings in a new issue comment in a collapsed block, like those of new issues.
// Comments by bots and by users with write access to the repository are left as they are.

const { wrapScreenshots } = require('./wrap-screenshots.js');

const WRITE_PERMISSIONS = ['admin', 'maintain', 'write'];

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const { comment, issue } = context.payload;
  const live = process.env.TRIAGE_MODE === 'live';

  if (comment.user.type === 'Bot') {
    core.info(`Comment ${comment.html_url} is by a bot`);
    return;
  }

  const body = wrapScreenshots(comment.body);
  if (body === comment.body) {
    core.info(`Comment ${comment.html_url} has nothing to wrap`);
    return;
  }

  let permission = 'none';
  try {
    const { data } = await github.rest.repos.getCollaboratorPermissionLevel({
      owner,
      repo,
      username: comment.user.login,
    });
    permission = data.role_name || data.permission;
  } catch (error) {
    if (error.status !== 404) throw error;
  }
  if (WRITE_PERMISSIONS.includes(permission)) {
    core.info(`Comment ${comment.html_url} is by ${comment.user.login}, who has ${permission} access`);
    return;
  }

  core.info(`Wrapping the screenshots and recordings of the comment on #${issue.number}: ${comment.html_url}`);
  if (!live) return;
  await github.rest.issues.updateComment({ owner, repo, comment_id: comment.id, body });
};
