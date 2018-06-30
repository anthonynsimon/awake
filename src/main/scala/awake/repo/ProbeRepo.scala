package awake.repo

import awake.domain.ProbeId
import awake.probe.Probe

trait ProbeRepo {
  def lookup(id: ProbeId): Option[Probe]
}

case class StaticProbeRepo(probes: Map[ProbeId, Probe]) extends ProbeRepo {
  override def lookup(id: ProbeId): Option[Probe] = probes.get(id)
}