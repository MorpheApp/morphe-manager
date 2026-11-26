// Wraps screenshots and screen recordings in an issue or comment body in a collapsed <details> block, so they
// do not fill the page. Each group of screenshot and recording lines, which may be separated by blank lines,
// gets its own block. Those that are already in a <details> block or in a code block,
// and those in a line with other text, are left as they are.

// <img ...> or ![alt](url)
const IMAGE = /<img\b[^>]*>|!\[[^\]\n]*\]\([^)\n]*\)/gi;

// A line that is only an attachment url, which is how GitHub adds an uploaded video, or only a <video> tag.
const ATTACHMENT_URL_LINE =
  /^(?:https:\/\/github\.com\/user-attachments\/assets\/[\w-]+|https:\/\/user-images\.githubusercontent\.com\/\S+\.(?:mp4|mov|webm))$/i;
const VIDEO_TAG_LINE = /^<video\b[^>]*>[^<]*<\/video>$/i;

function isImageLine(line) {
  const images = line.match(IMAGE);
  return images !== null && line.replace(IMAGE, '').trim() === '';
}

function isVideoLine(line) {
  const text = line.trim();
  return ATTACHMENT_URL_LINE.test(text) || VIDEO_TAG_LINE.test(text);
}

function summaryText(imageCount, videoCount) {
  const images = imageCount === 1 ? 'Screenshot' : 'Screenshots';
  const plural = videoCount === 1 ? '' : 's';
  if (videoCount === 0) return images;
  if (imageCount === 0) return `Screen recording${plural}`;
  return `${images} and screen recording${plural}`;
}

function detailsBlock(lines) {
  let imageCount = 0;
  let videoCount = 0;
  const content = [];
  let previousIsVideo = false;

  for (const line of lines) {
    const isVideo = isVideoLine(line);
    if (isVideo) videoCount++;
    else imageCount += (line.match(IMAGE) || []).length;

    // GitHub only shows a player if the url is a paragraph of its own.
    const last = content[content.length - 1];
    if ((isVideo || previousIsVideo) && last !== undefined && last.trim() !== '' && line.trim() !== '') {
      content.push('');
    }
    content.push(line);
    previousIsVideo = isVideo;
  }

  return [
    '<details>',
    `<summary>${summaryText(imageCount, videoCount)}</summary>`,
    // The Markdown inside the block only renders with a blank line after the summary.
    '',
    ...content,
    '',
    '</details>',
  ];
}

/**
 * Returns the body with its screenshots and screen recordings wrapped in <details> blocks, or the same body if there is nothing to wrap.
 */
function wrapScreenshots(body) {
  if (!body) return body;

  const lines = body.split(/\r?\n/);
  const result = [];
  let fence = null;
  let detailsDepth = 0;
  let group = [];
  // Blank lines after the last line of a group, added back if no line of the group follows.
  let pendingBlank = [];

  const flush = () => {
    if (group.length > 0) {
      result.push(...detailsBlock(group));
      group = [];
    }
    result.push(...pendingBlank);
    pendingBlank = [];
  };

  for (const line of lines) {
    const fenceMatch = /^\s*(`{3,}|~{3,})/.exec(line);
    if (fence) {
      if (fenceMatch && fenceMatch[1][0] === fence[0] && fenceMatch[1].length >= fence.length) fence = null;
      result.push(line);
      continue;
    }

    if (detailsDepth === 0 && (isImageLine(line) || isVideoLine(line))) {
      // Blank lines between screenshots and recordings stay inside the block.
      group.push(...pendingBlank, line.trim());
      pendingBlank = [];
      continue;
    }

    if (group.length > 0 && line.trim() === '') {
      pendingBlank.push(line);
      continue;
    }

    // The Markdown after the block only renders with a blank line between them.
    if (group.length > 0 && pendingBlank.length === 0) pendingBlank.push('');
    flush();

    if (fenceMatch) {
      fence = fenceMatch[1];
    } else {
      detailsDepth += (line.match(/<details\b/gi) || []).length;
      detailsDepth -= (line.match(/<\/details>/gi) || []).length;
      if (detailsDepth < 0) detailsDepth = 0;
    }
    result.push(line);
  }
  flush();

  const wrapped = result.join('\n');
  return wrapped === lines.join('\n') ? body : wrapped;
}

module.exports = { wrapScreenshots };
