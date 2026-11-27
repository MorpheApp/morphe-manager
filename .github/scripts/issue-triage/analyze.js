// Analyzes a new issue with an AI model through the OpenRouter API, and sets the "decision" output
// to the triage decision as JSON. apply.js validates the decision and makes the changes.
//
// The model can search and read other issues of the repository with the tools below, to find duplicates.
// The issue text is written by anyone, so the model and its reply are treated as untrusted. This job only
// has read access, the tools only read issues of this repository, and apply.js validates every field.
// The prompt and the fields of the decision are in triage-prompt.js.

const { escapeHtml } = require('./common.js');
const { SYSTEM_PROMPT, FLAG_FIELD, MISSING_INFO_KEYS } = require('./triage-prompt.js');

// Tried in order, until one returns a usable decision, such as when a model is not available.
const MODELS = ['google/gemma-4-31b-it', 'google/gemma-4-26b-a4b-it', 'google/gemma-3-27b-it'];

const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';

const REQUEST_TIMEOUT_MS = 90 * 1000;
// Each model gets this long to finish, so the last model still gets a turn before the job times out.
const MODEL_TIME_LIMIT_MS = 3 * 60 * 1000;
const MAX_REPLY_TOKENS = 4000;
// Requests for one model. The last one cannot use tools, so the model must reply with its decision.
const MAX_REQUESTS = 14;

const MAX_BODY_CHARS = 12000;
const MAX_RESULT_BODY_CHARS = 3000;
const MAX_SEARCH_RESULTS = 20;
const MAX_RATE_LIMIT_WAIT_MS = 65 * 1000;

const TOOLS = [
  {
    type: 'function',
    function: {
      name: 'search_issues',
      description:
        'Searches the open and closed issues of the repository that are older than the issue being triaged. ' +
        'Returns up to 20 issues with their number, title, state and creation date, best matches first.',
      parameters: {
        type: 'object',
        properties: {
          query: { type: 'string', description: 'Keywords to search for, such as a setting name or a symptom' },
        },
        required: ['query'],
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'view_issue',
      description: 'Reads an issue of the repository: its title, body, state, labels and creation date.',
      parameters: {
        type: 'object',
        properties: {
          number: { type: 'integer', description: 'The issue number' },
        },
        required: ['number'],
      },
    },
  },
];

function truncate(text, maxChars) {
  const value = text || '';
  return value.length > maxChars ? `${value.slice(0, maxChars)}\n[truncated]` : value;
}

/**
 * Text for the log. JSON keeps untrusted text on one line, so it can never start a workflow command.
 */
function logText(value) {
  return JSON.stringify(value).slice(0, 300);
}

function isRateLimited(error) {
  return error.status === 429 || (error.status === 403 && /rate limit/i.test(error.message));
}

/**
 * Runs a tool call of the model. Errors of the arguments are returned to the model instead of thrown.
 */
async function runTool({ github, core, owner, repo, issue }, name, args) {
  if (name === 'search_issues') {
    // Qualifiers could search other repositories or pull requests, so only the keywords are kept.
    // They are put in parentheses, as an OR could otherwise also match issues outside the repository.
    const keywords = String(args.query || '')
      .replace(/\b[\w-]+:("[^"]*"|\S+)/g, ' ')
      .replace(/[()]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 200);
    if (!keywords) return { error: 'The query has no keywords' };

    const search = () =>
      github.request('GET /search/issues', {
        q: `(${keywords}) repo:${owner}/${repo} is:issue`,
        per_page: MAX_SEARCH_RESULTS + 5,
        advanced_search: 'true',
      });
    let data;
    try {
      ({ data } = await search());
    } catch (error) {
      if (!isRateLimited(error)) throw error;
      // The search API allows only a few searches per minute, so wait for the limit to reset once.
      const resetMs = Number(error.response?.headers?.['x-ratelimit-reset']) * 1000 - Date.now();
      const waitMs = Math.min(Math.max(Number.isFinite(resetMs) ? resetMs : 0, 1000) + 1000, MAX_RATE_LIMIT_WAIT_MS);
      core.warning(`The issue search is rate limited, waiting ${Math.round(waitMs / 1000)} seconds: ${error.message}`);
      await new Promise((resolve) => setTimeout(resolve, waitMs));
      try {
        ({ data } = await search());
      } catch (retryError) {
        if (!isRateLimited(retryError)) throw retryError;
        core.warning(`The issue search is still rate limited: ${retryError.message}`);
        return { error: 'The search is rate limited. Use the results you already have.' };
      }
    }
    return data.items
      .filter((item) => item.number < issue.number && !item.pull_request)
      .slice(0, MAX_SEARCH_RESULTS)
      .map((item) => ({
        number: item.number,
        title: item.title,
        state: item.state,
        created_at: item.created_at,
      }));
  }

  if (name === 'view_issue') {
    const number = Number(args.number);
    if (!Number.isInteger(number) || number <= 0) return { error: 'Invalid issue number' };
    if (number >= issue.number) return { error: 'Only issues older than the issue being triaged can be read' };

    let data;
    try {
      ({ data } = await github.rest.issues.get({ owner, repo, issue_number: number }));
    } catch (error) {
      if (error.status !== 404 && error.status !== 410) throw error;
      return { error: `Issue ${number} does not exist` };
    }
    if (data.pull_request) return { error: `${number} is a pull request` };
    return {
      number: data.number,
      title: data.title,
      state: data.state,
      state_reason: data.state_reason,
      labels: data.labels.map((label) => (typeof label === 'string' ? label : label.name)),
      created_at: data.created_at,
      body: truncate(data.body, MAX_RESULT_BODY_CHARS),
    };
  }

  return { error: `Unknown tool: ${name}` };
}

/**
 * Parses the reply with the decision. Throws if it is not usable, so the next model is tried.
 */
function parseDecision(content) {
  if (typeof content !== 'string') throw new Error('The reply has no text');

  // Some models wrap the JSON in a code block or add text around it.
  const start = content.indexOf('{');
  const end = content.lastIndexOf('}');
  if (start === -1 || end < start) throw new Error(`The reply has no JSON object: ${logText(content)}`);

  const reply = JSON.parse(content.slice(start, end + 1));
  if (!['bug', 'feature', 'question'].includes(reply.type)) {
    throw new Error(`The reply has an invalid type: ${logText(reply.type)}`);
  }
  if (typeof reply[FLAG_FIELD] !== 'boolean') {
    throw new Error(`The reply has an invalid ${FLAG_FIELD}: ${logText(reply[FLAG_FIELD])}`);
  }

  const duplicateOf = Number(reply.duplicate_of || 0);
  const possible = Array.isArray(reply.possible_duplicates) ? reply.possible_duplicates : [];
  const missing = Array.isArray(reply.missing_info) ? reply.missing_info : [];
  return {
    title: typeof reply.title === 'string' ? reply.title : '',
    type: reply.type,
    [FLAG_FIELD]: reply[FLAG_FIELD],
    duplicate_of: Number.isInteger(duplicateOf) && duplicateOf > 0 ? duplicateOf : 0,
    possible_duplicates: possible
      .map(Number)
      .filter((number) => Number.isInteger(number) && number > 0)
      .slice(0, 3),
    missing_info: [...new Set(missing.filter((key) => MISSING_INFO_KEYS.includes(key)))],
    reasoning: typeof reply.reasoning === 'string' ? reply.reasoning : '',
  };
}

async function request({ model, messages, apiKey, toolChoice, fetchImpl }) {
  const response = await fetchImpl(OPENROUTER_URL, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model,
      messages,
      tools: TOOLS,
      tool_choice: toolChoice,
      temperature: 0,
      max_tokens: MAX_REPLY_TOKENS,
      usage: { include: true },
    }),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });

  const text = await response.text();
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}: ${text.slice(0, 500)}`);
    error.status = response.status;
    throw error;
  }

  const data = JSON.parse(text);
  // OpenRouter can report an error of the provider with a 200 status.
  if (data.error) throw new Error(`Provider error: ${JSON.stringify(data.error).slice(0, 500)}`);
  const message = data.choices?.[0]?.message;
  if (!message) throw new Error(`The reply has no message: ${text.slice(0, 500)}`);
  return { message, cost: Number(data.usage?.cost) || 0 };
}

/**
 * Lets one model search and read issues until it replies with a decision.
 */
async function analyzeWithModel({ model, issueMessage, apiKey, tools, core, fetchImpl, timeLimitMs }) {
  const deadline = Date.now() + timeLimitMs;
  const messages = [
    { role: 'system', content: SYSTEM_PROMPT },
    { role: 'user', content: issueMessage },
  ];
  const usage = { requests: 0, searches: 0, reads: 0, cost: 0 };
  let askedToSearch = false;
  let askedToFix = false;
  // Issue numbers the tools returned. Only these can be duplicates, as a model that did not search
  // could otherwise make up a number.
  const seenIssues = new Set();

  for (let i = 0; i < MAX_REQUESTS; i++) {
    if (Date.now() > deadline) throw new Error('The model took too long');

    const last = i === MAX_REQUESTS - 1;
    if (last) messages.push({ role: 'user', content: 'Stop searching and reply now with only the JSON object.' });

    const { message, cost } = await request({
      model,
      messages,
      apiKey,
      toolChoice: last ? 'none' : 'auto',
      fetchImpl,
    });
    usage.requests++;
    usage.cost += cost;

    const toolCalls = message.tool_calls || [];
    if (toolCalls.length === 0) {
      let decision;
      try {
        decision = parseDecision(message.content);
      } catch (error) {
        // The model is asked once to correct its reply, which is faster than starting over with the next model.
        if (askedToFix || last) throw error;
        askedToFix = true;
        core.info(`${model} reply is not usable, asking to correct it: ${error.message}`);
        messages.push(
          { role: 'assistant', content: message.content || '' },
          { role: 'user', content: `Your reply is not usable: ${error.message}. Reply with only the JSON object.` }
        );
        continue;
      }
      // Duplicates are only found by searching.
      if (usage.searches === 0 && !decision[FLAG_FIELD] && !askedToSearch && !last) {
        askedToSearch = true;
        messages.push(
          { role: 'assistant', content: message.content || '' },
          { role: 'user', content: 'You did not search for duplicates. Do step 5, then reply with the JSON object.' }
        );
        continue;
      }
      const isSeen = (number) => seenIssues.has(number);
      if ((decision.duplicate_of && !isSeen(decision.duplicate_of)) || !decision.possible_duplicates.every(isSeen)) {
        core.warning(
          `${model} named duplicates that the tools did not return, ignoring them: ` +
            `${decision.duplicate_of}, ${decision.possible_duplicates.join(', ')}`
        );
        if (!isSeen(decision.duplicate_of)) decision.duplicate_of = 0;
        decision.possible_duplicates = decision.possible_duplicates.filter(isSeen);
      }
      return { decision, usage };
    }

    messages.push({ role: 'assistant', content: message.content || '', tool_calls: toolCalls });
    for (const call of toolCalls) {
      let args;
      try {
        args = JSON.parse(call.function?.arguments || '{}');
      } catch (error) {
        core.info(`Invalid tool arguments: ${logText(call.function?.arguments)} (${error.message})`);
        args = {};
      }
      const name = call.function?.name;
      core.info(`${model} tool call: ${logText(name)} ${logText(args)}`);
      if (name === 'search_issues') usage.searches++;
      if (name === 'view_issue') usage.reads++;

      const result = await tools(name, args);
      for (const item of Array.isArray(result) ? result : [result]) {
        if (Number.isInteger(item?.number)) seenIssues.add(item.number);
      }
      messages.push({ role: 'tool', tool_call_id: call.id, content: JSON.stringify(result) });
    }
  }

  throw new Error('The model did not reply with a decision');
}

/**
 * Tries the models in order until one returns a usable decision.
 */
async function analyze({
  models = MODELS,
  issueMessage,
  apiKey,
  tools,
  core,
  fetchImpl = fetch,
  timeLimitMs = MODEL_TIME_LIMIT_MS,
}) {
  const failures = [];
  for (const model of models) {
    try {
      const result = await analyzeWithModel({ model, issueMessage, apiKey, tools, core, fetchImpl, timeLimitMs });
      return { ...result, model, failures };
    } catch (error) {
      core.warning(`${model} failed: ${error.message}`);
      failures.push(`${model}: ${error.message}`);
      // A bad key, or no credits left, fails with every model.
      if (error.status === 401 || error.status === 402) break;
    }
  }
  throw new Error(`No model returned a usable decision.\n${failures.join('\n')}`);
}

/**
 * The first message of the conversation: the issue, and whether it is closed for its versions.
 */
function buildIssueMessage(issue, versionProblems) {
  const lines = [];
  if (versionProblems.length > 0) {
    lines.push(
      'This issue will be closed because of its versions, but still look for duplicates so the author ' +
        'can be told about them. Set missing_info to an empty list.',
      ''
    );
  }
  lines.push(
    'The issue to triage, as JSON:',
    JSON.stringify({
      number: issue.number,
      title: issue.title,
      created_at: issue.created_at,
      body: truncate(issue.body, MAX_BODY_CHARS),
    })
  );
  return lines.join('\n');
}

module.exports = async ({ github, context, core }) => {
  const { owner, repo } = context.repo;
  const issueNumber = Number(process.env.ISSUE_NUMBER);
  if (!Number.isInteger(issueNumber) || issueNumber <= 0) throw new Error('No valid issue number');

  const apiKey = (process.env.OPENROUTER_API_KEY || '').trim();
  if (!apiKey) throw new Error('The OPENROUTER_TRIAGE_KEY secret is not set');

  const versionProblems = JSON.parse(process.env.VERSION_PROBLEMS || '[]');
  const { data: issue } = await github.rest.issues.get({ owner, repo, issue_number: issueNumber });

  const { decision, model, usage, failures } = await analyze({
    issueMessage: buildIssueMessage(issue, versionProblems),
    apiKey,
    tools: (name, args) => runTool({ github, core, owner, repo, issue }, name, args),
    core,
  });

  core.info(`Decision of ${model}: ${JSON.stringify(decision)}`);
  core.setOutput('decision', JSON.stringify(decision));

  await core.summary
    .addHeading(`Analysis of #${issueNumber}`, 3)
    .addList(
      [
        `Model: ${model}`,
        `Requests: ${usage.requests}, searches: ${usage.searches}, issues read: ${usage.reads}`,
        `Cost: $${usage.cost.toFixed(4)}`,
        ...failures.map((failure) => `Failed: ${failure.slice(0, 200)}`),
      ].map(escapeHtml)
    )
    .write();
};

Object.assign(module.exports, { MODELS, analyze, parseDecision, buildIssueMessage, runTool });
