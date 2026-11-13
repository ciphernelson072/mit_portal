const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const pack = path.resolve(__dirname, '..');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'portal-office-'));
const cpFile = path.join(temp, 'classpath.txt');
const javaFile = path.join(__dirname, 'OfficePackGenerator.java');
const run = (command, args) => {
  const result = spawnSync(command, args, {
    cwd: path.resolve(pack, '..', '..'),
    encoding: 'utf8',
    windowsHide: true,
    shell: process.platform === 'win32',
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} failed with status ${result.status}`);
};

try {
  run('mvn.cmd', ['-q', 'dependency:build-classpath', '-Dmdep.outputFile=' + cpFile, '-DincludeScope=compile']);
  const classpath = fs.readFileSync(cpFile, 'utf8').trim();
  if (!classpath) throw new Error('Maven returned an empty dependency classpath.');
  const classes = path.join(temp, 'classes');
  fs.mkdirSync(classes);
  const javaPath = value => value.replace(/\\/g, '/');
  const quoted = value => `"${javaPath(value).replace(/"/g, '\\"')}"`;
  const javaClasspath = javaPath(classpath);
  const compileArgs = path.join(temp, 'compile.args');
  fs.writeFileSync(compileArgs, [
    '-encoding', 'UTF-8', '-proc:none', '-cp', quoted(javaClasspath), '-d', quoted(classes), quoted(javaFile),
  ].join('\r\n'), 'utf8');
  run('javac.exe', ['@' + compileArgs]);
  const runArgs = path.join(temp, 'run.args');
  fs.writeFileSync(runArgs, [
    '-cp', quoted(javaPath(classes) + path.delimiter + javaClasspath), 'OfficePackGenerator', quoted(pack),
  ].join('\r\n'), 'utf8');
  run('java.exe', ['@' + runArgs]);
  console.log(`Created Office deliverables in ${pack}`);
} finally {
  fs.rmSync(temp, { recursive: true, force: true });
}
