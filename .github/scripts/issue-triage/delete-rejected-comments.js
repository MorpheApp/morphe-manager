// Deletes comments of the triage workflows that a maintainer rejected, by adding a thumbs down or by hiding
// the comment. The label that came with the comment is removed too.
// Only comments from the last 30 days are checked.

const {
  LABEL_POSSIBLE_DUPLICATE,
  LABEL_WAITING_ON_AUTHOR,
  MISSING_INFO_MARKER,
  POSSIBLE_DUPLICATE_PREFIX,
  removeLabelIfPresent,
} = require('./common.js');

const BOT_LOGIN = 'github-actions[bot]';
const CHECK_DAYS = 30;
// Repository roles that can reject a comment. Triage is included, as it can hide comments.
const MAINTAINER_PERMISSIONS = ['admin', 'maintain', 'write', 'triage'];

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const live = process.env.TRIAGE_MODE === 'live';
  const since = new Date(Date.now() - CHECK_DAYS * 24 * 60 * 60 * 1000).toISOString();

  const comments = (
    await github.paginate(github.rest.issues.listCommentsForRepo, { owner, repo, since, per_page: 100 })
  ).filter((comment) => comment.user && comment.user.login === BOT_LOGIN);
  if (comments.length === 0) return;

  // Hidden comments are only available with GraphQL.
  const minimized = new Set();
  for (let i = 0; i < comments.length; i += 100) {
    const ids = comments.slice(i, i + 100).map((comment) => comment.node_id);
    const { nodes } = await github.graphql(
      `query($ids: [ID!]!) {
        nodes(ids: $ids) {
          ... on IssueComment {
            id
            isMinimized
          }
        }
      }`,
      { ids }
    );
    nodes.filter((node) => node && node.isMinimized).forEach((node) => minimized.add(node.id));
  }

  const permissions = new Map();
  const isMaintainer = async (login) => {
    if (!permissions.has(login)) {
      let permission = 'none';
      try {
        const { data } = await github.rest.repos.getCollaboratorPermissionLevel({ owner, repo, username: login });
        permission = data.role_name || data.permission;
      } catch (error) {
        if (error.status !== 404) throw error;
      }
      permissions.set(login, MAINTAINER_PERMISSIONS.includes(permission));
    }
    return permissions.get(login);
  };

  for (const comment of comments) {
    const issueNumber = Number(comment.issue_url.split('/').pop());

    let reason = null;
    if (minimized.has(comment.node_id)) {
      reason = 'it was hidden';
    } else if (comment.reactions && comment.reactions['-1'] > 0) {
      const reactions = await github.paginate(github.rest.reactions.listForIssueComment, {
        owner,
        repo,
        comment_id: comment.id,
        content: '-1',
        per_page: 100,
      });
      for (const reaction of reactions) {
        if (reaction.user && (await isMaintainer(reaction.user.login))) {
          reason = `${reaction.user.login} added a thumbs down`;
          break;
        }
      }
    }
    if (!reason) continue;

    core.info(`Deleting the comment on #${issueNumber} because ${reason}: ${comment.html_url}`);
    if (!live) continue;

    await github.rest.issues.deleteComment({ owner, repo, comment_id: comment.id });
    if (comment.body.startsWith(POSSIBLE_DUPLICATE_PREFIX)) {
      await removeLabelIfPresent(github, owner, repo, issueNumber, LABEL_POSSIBLE_DUPLICATE);
    } else if (comment.body.startsWith(MISSING_INFO_MARKER)) {
      await removeLabelIfPresent(github, owner, repo, issueNumber, LABEL_WAITING_ON_AUTHOR);
    }
  }
};
