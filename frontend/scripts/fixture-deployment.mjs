/** Wait for the explicitly requested fixture deployment, never infer success from request acceptance. */
export async function waitForFixtureDeployment(api, deploymentId) {
  if (!deploymentId) throw new Error('Fixture deployment ID is missing')
  const deadline = Date.now() + 60000
  do {
    const deployment = await api('GET', `/function-units/deployments/${deploymentId}/status`)
    if (deployment.status === 'SUCCESS') return deployment
    if (deployment.status === 'FAILED' || deployment.status === 'ROLLED_BACK') throw new Error('Fixture deployment failed')
    if (!['PENDING', 'DEPLOYING'].includes(deployment.status)) throw new Error('Unknown fixture deployment status')
    await new Promise(resolve => setTimeout(resolve, 500))
  } while (Date.now() < deadline)
  throw new Error('Fixture deployment did not complete within its deadline')
}
