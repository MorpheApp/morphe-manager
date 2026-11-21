// Deletes new issues that were not created with an issue form, after a comment that tells the author why.
// Wraps screenshots in new issues in a collapsed block.
// Closes bug reports for outdated Morphe versions.
// Sets the "triage" output to "true" if the issue should go on to the analyze job,
// and the "closed" output to "true" if it was closed here. A closed issue is still analyzed for its title.

const { isFromTemplate, isMaintainer, commentAndDelete } = require('./common.js');
const { checkVersions } = require('./check-versions.js');
const { wrapScreenshots } = require('./wrap-screenshots.js');

module.exports = async ({ github, getOctokit, context, core }) => {
  const { owner, repo } = context.repo;
  const issueNumber = context.payload.issue
    ? context.payload.issue.number
    : Number(context.payload.inputs.issue_number);

  const { data: issue } = await github.rest.issues.get({ owner, repo, issue_number: issueNumber });

  if (issue.pull_request) {
    core.info(`#${issueNumber} is a pull request`);
    core.setOutput('triage', 'false');
    return;
  }

  if (isMaintainer(issue)) {
    core.info(`#${issueNumber} was opened by a maintainer (${issue.author_association}), skipping`);
    core.setOutput('triage', 'false');
    return;
  }

  if (isFromTemplate(issue.body)) {
    core.info(`#${issueNumber} uses an issue form`);

    const body = wrapScreenshots(issue.body);
    if (body !== issue.body) {
      core.info(`Wrapping the screenshots of #${issueNumber}`);
      try {
        await github.rest.issues.update({ owner, repo, issue_number: issueNumber, body });
      } catch (error) {
        core.warning(`Could not wrap the screenshots of #${issueNumber}: ${error.message}`);
      }
    }

    // Manual runs only triage, they never close issues.
    const closed =
      context.eventName !== 'workflow_dispatch' && (await checkVersions({ github, core, owner, repo, issue }));
    core.setOutput('triage', 'true');
    core.setOutput('closed', closed ? 'true' : 'false');
    return;
  }

  // Manual runs only triage, they never delete issues.
  if (context.eventName === 'workflow_dispatch') {
    core.info(`#${issueNumber} does not use an issue form, triaging anyway for a manual run`);
    core.setOutput('triage', 'true');
    return;
  }

  core.info(`#${issueNumber} does not use an issue form, deleting it`);
  await commentAndDelete({
    github,
    getOctokit,
    core,
    owner,
    repo,
    issue,
    comment: [
      'This issue was deleted because it was not created with the bug report or feature request form.',
      '',
      `To report a bug or request a feature, open a new issue and choose one of the forms: https://github.com/${owner}/${repo}/issues/new/choose`,
    ].join('\n'),
  });

  core.setOutput('triage', 'false');
};
