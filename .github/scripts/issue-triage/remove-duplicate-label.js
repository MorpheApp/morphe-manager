// Removes the "possible duplicate" label from an issue that was closed as a duplicate,
// as the label is only for issues that a maintainer has not decided on yet.

const { LABEL_POSSIBLE_DUPLICATE, removeLabelIfPresent } = require('./common.js');

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const { issue } = context.payload;
  const live = process.env.TRIAGE_MODE === 'live';

  if (issue.state_reason !== 'duplicate') {
    core.info(`#${issue.number} was not closed as a duplicate (${issue.state_reason})`);
    return;
  }
  if (!issue.labels.some((label) => label.name === LABEL_POSSIBLE_DUPLICATE)) {
    core.info(`#${issue.number} has no "${LABEL_POSSIBLE_DUPLICATE}" label`);
    return;
  }

  core.info(`#${issue.number} was closed as a duplicate, removing the "${LABEL_POSSIBLE_DUPLICATE}" label`);
  if (!live) return;
  await removeLabelIfPresent(github, owner, repo, issue.number, LABEL_POSSIBLE_DUPLICATE);
};
